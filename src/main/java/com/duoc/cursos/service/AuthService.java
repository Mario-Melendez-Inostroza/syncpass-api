package com.duoc.cursos.service;

import com.duoc.cursos.exception.AuthChallengeException;
import com.duoc.cursos.exception.RefreshUsernameRequiredException;
import com.duoc.cursos.model.Usuario;
import com.duoc.cursos.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /**
     * Rol que el servidor asigna a todo usuario registrado. Debe existir como grupo en el User Pool.
     * El cliente no puede elegirlo.
     */
    public static final String ROL_POR_DEFECTO = "USER";

    private final CognitoIdentityProviderClient cognitoClient;
    private final UsuarioRepository usuarioRepository;

    @Value("${aws.cognito.client-id}")
    private String clientId;

    @Value("${aws.cognito.user-pool-id}")
    private String userPoolId;

    @Value("${aws.cognito.client-secret:}")
    private String clientSecret;

    public AuthService(CognitoIdentityProviderClient cognitoClient, UsuarioRepository usuarioRepository) {
        this.cognitoClient = cognitoClient;
        this.usuarioRepository = usuarioRepository;
    }

    /**
     * FLUJO: REGISTRO, CONFIRMACIÓN AUTOMÁTICA Y COPIA LOCAL
     *
     * Orden: SignUp -> AdminConfirmSignUp -> AdminAddUserToGroup (rol del servidor) -> guardar en PostgreSQL.
     *
     * No es @Transactional a propósito: Cognito y PostgreSQL no comparten transacción, y con @Transactional
     * el commit (donde Hibernate detecta p. ej. un email duplicado) ocurriría después de este método,
     * fuera del bloque que compensa. Cada save() ya corre en su propia transacción.
     *
     * Si algo falla después del SignUp se intenta borrar el usuario de Cognito (compensación) para poder
     * reintentar el registro; si la compensación también falla, se registra una INCONSISTENCIA en el log.
     */
    public Usuario registrar(String email, String password, String nombre) {
        // 1. Registro en Cognito
        SignUpRequest.Builder signUpBuilder = SignUpRequest.builder()
                .clientId(clientId)
                .username(email)
                .password(password)
                .userAttributes(
                        AttributeType.builder().name("email").value(email).build(),
                        AttributeType.builder().name("name").value(nombre).build()
                );

        // Agregar SECRET_HASH si hay client secret
        if (clientSecret != null && !clientSecret.isBlank()) {
            signUpBuilder.secretHash(generateSecretHash(email));
        }

        SignUpResponse signUpResponse = cognitoClient.signUp(signUpBuilder.build());
        String cognitoSub = signUpResponse.userSub();

        try {
            // 2. Confirmación automática (sin código de verificación). Requiere cognito-idp:AdminConfirmSignUp.
            cognitoClient.adminConfirmSignUp(AdminConfirmSignUpRequest.builder()
                    .userPoolId(userPoolId)
                    .username(email)
                    .build());

            // 3. Asignación al grupo del rol por defecto. Requiere cognito-idp:AdminAddUserToGroup.
            cognitoClient.adminAddUserToGroup(AdminAddUserToGroupRequest.builder()
                    .userPoolId(userPoolId)
                    .username(email)
                    .groupName(ROL_POR_DEFECTO)
                    .build());

            // 4. Persistencia en Base de Datos Local
            Usuario usuarioLocal = new Usuario();
            usuarioLocal.setId(cognitoSub);
            usuarioLocal.setEmail(email);
            usuarioLocal.setNombre(nombre);
            usuarioLocal.setRoles(new HashSet<>(Set.of(ROL_POR_DEFECTO)));

            return usuarioRepository.save(usuarioLocal);
        } catch (RuntimeException ex) {
            compensarRegistroFallido(email, cognitoSub, ex);
            throw ex;
        }
    }

    /**
     * Intenta borrar de Cognito al usuario recién creado cuando el registro no pudo completarse.
     * Es "mejor esfuerzo" (requiere cognito-idp:AdminDeleteUser): nunca oculta la excepción original.
     * No se registran contraseñas, tokens ni el email; se identifica al usuario por su sub.
     */
    private void compensarRegistroFallido(String email, String cognitoSub, RuntimeException causa) {
        log.error("Registro fallido tras crear el usuario en Cognito (sub={}). Causa: {}",
                cognitoSub, causa.getClass().getSimpleName(), causa);
        try {
            cognitoClient.adminDeleteUser(AdminDeleteUserRequest.builder()
                    .userPoolId(userPoolId)
                    .username(email)
                    .build());
            log.warn("Usuario sub={} eliminado de Cognito para permitir reintentar el registro", cognitoSub);
        } catch (RuntimeException ex) {
            log.error("INCONSISTENCIA: el usuario sub={} quedó en Cognito sin registro local completo y no se pudo "
                    + "revertir ({}). Eliminarlo manualmente o completar su alta.",
                    cognitoSub, ex.getClass().getSimpleName());
        }
    }

    /**
     * FLUJO: INICIO DE SESIÓN (LOGIN) PROXY
     */
    public AuthenticationResultType login(String email, String password) {
        InitiateAuthRequest.Builder authBuilder = InitiateAuthRequest.builder()
                .authFlow(AuthFlowType.USER_PASSWORD_AUTH)
                .clientId(clientId)
                .authParameters(Map.of(
                        "USERNAME", email,
                        "PASSWORD", password
                ));

        if (clientSecret != null && !clientSecret.isBlank()) {
            authBuilder.authParameters(Map.of(
                    "USERNAME", email,
                    "PASSWORD", password,
                    "SECRET_HASH", generateSecretHash(email)
            ));
        }

        InitiateAuthResponse authResponse = cognitoClient.initiateAuth(authBuilder.build());
        AuthenticationResultType result = authResponse.authenticationResult();
        if (result == null) {
            // Cognito respondió con un reto (p. ej. NEW_PASSWORD_REQUIRED) que este endpoint no implementa.
            throw new AuthChallengeException(authResponse.challengeNameAsString());
        }
        return result;
    }

    /**
     * FLUJO: RENOVACIÓN DE TOKENS (REFRESH)
     *
     * Usa el flujo REFRESH_TOKEN_AUTH real de Cognito: no se fabrica ningún token localmente.
     * Cognito devuelve un accessToken y un idToken nuevos; en este flujo NO rota el refresh token,
     * por lo que authenticationResult().refreshToken() llega null y el cliente conserva el que ya tenía.
     *
     * Limitación real de Cognito: si el App Client tiene client secret, este flujo exige
     * SECRET_HASH = HMAC-SHA256(username + clientId). El refresh token es opaco (no es un JWT), así que
     * el username no se puede extraer de él y el cliente tiene que enviarlo. Si falta, se lanza
     * RefreshUsernameRequiredException en vez de llamar a Cognito con un hash que sabemos inválido.
     *
     * @param refreshToken refresh token entregado en el login
     * @param username     email del usuario; obligatorio solo si hay client secret configurado
     */
    public AuthenticationResultType refrescarTokens(String refreshToken, String username) {
        Map<String, String> authParameters = new HashMap<>();
        authParameters.put("REFRESH_TOKEN", refreshToken);

        if (clientSecret != null && !clientSecret.isBlank()) {
            if (username == null || username.isBlank()) {
                throw new RefreshUsernameRequiredException();
            }
            authParameters.put("SECRET_HASH", generateSecretHash(username));
        }

        InitiateAuthResponse authResponse = cognitoClient.initiateAuth(InitiateAuthRequest.builder()
                .authFlow(AuthFlowType.REFRESH_TOKEN_AUTH)
                .clientId(clientId)
                .authParameters(authParameters)
                .build());

        AuthenticationResultType result = authResponse.authenticationResult();
        if (result == null) {
            // Cognito pidió un reto en lugar de renovar (p. ej. MFA reconfigurada): no hay tokens.
            throw new AuthChallengeException(authResponse.challengeNameAsString());
        }
        return result;
    }

    /**
     * FLUJO: CIERRE DE SESIÓN GLOBAL (LOGOUT)
     */
    public void logout(String accessToken) {
        GlobalSignOutRequest signOutRequest = GlobalSignOutRequest.builder()
                .accessToken(accessToken)
                .build();
        cognitoClient.globalSignOut(signOutRequest);
    }

    /**
     * FLUJO: OLVIDÉ MI CONTRASEÑA - PASO 1 (SOLICITUD)
     */
    public void solicitarRecuperacionContrasena(String email) {
        ForgotPasswordRequest.Builder requestBuilder = ForgotPasswordRequest.builder()
                .clientId(clientId)
                .username(email);

        if (clientSecret != null && !clientSecret.isBlank()) {
            requestBuilder.secretHash(generateSecretHash(email));
        }

        cognitoClient.forgotPassword(requestBuilder.build());
    }

    /**
     * FLUJO: OLVIDÉ MI CONTRASEÑA - PASO 2 (CONFIRMACIÓN)
     */
    public void confirmarRecuperacionContrasena(String email, String codigoOtp, String nuevaContrasena) {
        ConfirmForgotPasswordRequest.Builder requestBuilder = ConfirmForgotPasswordRequest.builder()
                .clientId(clientId)
                .username(email)
                .confirmationCode(codigoOtp)
                .password(nuevaContrasena);

        if (clientSecret != null && !clientSecret.isBlank()) {
            requestBuilder.secretHash(generateSecretHash(email));
        }

        cognitoClient.confirmForgotPassword(requestBuilder.build());
    }

    /**
     * Genera SECRET_HASH usando HMAC-SHA256 (username + clientId + clientSecret)
     * Requerido cuando el App Client tiene "Generate client secret" activado
     */
    private String generateSecretHash(String username) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec secretKeySpec = 
                new javax.crypto.spec.SecretKeySpec(clientSecret.getBytes(), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal((username + clientId).getBytes());
            return java.util.Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Error generando SECRET_HASH", e);
        }
    }
}