package com.duoc.cursos.service;

import com.duoc.cursos.exception.AuthChallengeException;
import com.duoc.cursos.exception.RefreshUsernameRequiredException;
import com.duoc.cursos.model.Usuario;
import com.duoc.cursos.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias de AuthService con Cognito y el repositorio mockeados.
 * No usan credenciales AWS ni servicios externos.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String EMAIL = "usuario.prueba@example.com";
    private static final String PASSWORD = "Password-de-prueba-1!";
    private static final String NOMBRE = "Usuario Prueba";
    private static final String SUB = "sub-ficticio-123";

    @Mock
    private CognitoIdentityProviderClient cognito;

    @Mock
    private UsuarioRepository usuarioRepository;

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(cognito, usuarioRepository);
        ReflectionTestUtils.setField(service, "clientId", "client-de-prueba");
        ReflectionTestUtils.setField(service, "userPoolId", "pool-de-prueba");
        ReflectionTestUtils.setField(service, "clientSecret", "");
    }

    private void cognitoRegistraOk() {
        when(cognito.signUp(any(SignUpRequest.class)))
                .thenReturn(SignUpResponse.builder().userSub(SUB).build());
    }

    private void repositorioGuardaOk() {
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static CognitoIdentityProviderException accesoDenegado() {
        return (CognitoIdentityProviderException) CognitoIdentityProviderException.builder()
                .message("detalle interno de AWS")
                .awsErrorDetails(AwsErrorDetails.builder().errorCode("AccessDeniedException").build())
                .build();
    }

    // ------------------------------------------------------------------ registro: rol

    @Test
    @DisplayName("El rol USER lo asigna el servidor: grupo de Cognito y registro local")
    void registrar_asignaRolUserDesdeElServidor() {
        cognitoRegistraOk();
        repositorioGuardaOk();

        Usuario resultado = service.registrar(EMAIL, PASSWORD, NOMBRE);

        ArgumentCaptor<AdminAddUserToGroupRequest> grupo = ArgumentCaptor.forClass(AdminAddUserToGroupRequest.class);
        verify(cognito).adminAddUserToGroup(grupo.capture());
        assertEquals("MONITOR", grupo.getValue().groupName());
        assertEquals(EMAIL, grupo.getValue().username());
        assertEquals("pool-de-prueba", grupo.getValue().userPoolId());

        ArgumentCaptor<Usuario> guardado = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(guardado.capture());
        assertEquals(SUB, guardado.getValue().getId());
        assertEquals(EMAIL, guardado.getValue().getEmail());
        assertEquals(NOMBRE, guardado.getValue().getNombre());
        assertEquals(Set.of("MONITOR"), guardado.getValue().getRoles());
        assertEquals(Set.of("MONITOR"), resultado.getRoles());
    }

    @Test
    @DisplayName("Solo se asigna un grupo, y nunca uno privilegiado")
    void registrar_asignaUnUnicoGrupo() {
        cognitoRegistraOk();
        repositorioGuardaOk();

        service.registrar(EMAIL, PASSWORD, NOMBRE);

        verify(cognito, times(1)).adminAddUserToGroup(any(AdminAddUserToGroupRequest.class));
    }

    // ------------------------------------------------------------------ registro: orden

    @Test
    @DisplayName("Orden: SignUp -> AdminConfirmSignUp -> AdminAddUserToGroup -> guardar en BD")
    void registrar_ejecutaLosPasosEnOrden() {
        cognitoRegistraOk();
        repositorioGuardaOk();

        service.registrar(EMAIL, PASSWORD, NOMBRE);

        InOrder orden = inOrder(cognito, usuarioRepository);
        orden.verify(cognito).signUp(any(SignUpRequest.class));
        orden.verify(cognito).adminConfirmSignUp(any(AdminConfirmSignUpRequest.class));
        orden.verify(cognito).adminAddUserToGroup(any(AdminAddUserToGroupRequest.class));
        orden.verify(usuarioRepository).save(any(Usuario.class));
        verify(cognito, never()).adminDeleteUser(any(AdminDeleteUserRequest.class));
    }

    @Test
    @DisplayName("La confirmación automática usa el pool y el email, sin código de verificación")
    void registrar_confirmaAutomaticamente() {
        cognitoRegistraOk();
        repositorioGuardaOk();

        service.registrar(EMAIL, PASSWORD, NOMBRE);

        ArgumentCaptor<AdminConfirmSignUpRequest> confirmacion = ArgumentCaptor.forClass(AdminConfirmSignUpRequest.class);
        verify(cognito).adminConfirmSignUp(confirmacion.capture());
        assertEquals("pool-de-prueba", confirmacion.getValue().userPoolId());
        assertEquals(EMAIL, confirmacion.getValue().username());
    }

    // ------------------------------------------------------------------ registro: fallos

    @Test
    @DisplayName("Si falla el SignUp no se confirma, ni se asigna grupo, ni se guarda, ni se compensa")
    void registrar_falloEnSignUp_noHaceNada() {
        UsernameExistsException existe = UsernameExistsException.builder().message("ya existe").build();
        when(cognito.signUp(any(SignUpRequest.class))).thenThrow(existe);

        assertSame(existe, assertThrows(UsernameExistsException.class,
                () -> service.registrar(EMAIL, PASSWORD, NOMBRE)));

        verify(cognito, never()).adminConfirmSignUp(any(AdminConfirmSignUpRequest.class));
        verify(cognito, never()).adminAddUserToGroup(any(AdminAddUserToGroupRequest.class));
        verify(cognito, never()).adminDeleteUser(any(AdminDeleteUserRequest.class));
        verifyNoInteractions(usuarioRepository);
    }

    @Test
    @DisplayName("Si falla la confirmación (permisos IAM) se propaga el error y se revierte en Cognito")
    void registrar_falloEnConfirmacion_propagaYCompensa() {
        cognitoRegistraOk();
        CognitoIdentityProviderException denegado = accesoDenegado();
        when(cognito.adminConfirmSignUp(any(AdminConfirmSignUpRequest.class))).thenThrow(denegado);

        assertSame(denegado, assertThrows(CognitoIdentityProviderException.class,
                () -> service.registrar(EMAIL, PASSWORD, NOMBRE)));

        ArgumentCaptor<AdminDeleteUserRequest> borrado = ArgumentCaptor.forClass(AdminDeleteUserRequest.class);
        verify(cognito).adminDeleteUser(borrado.capture());
        assertEquals(EMAIL, borrado.getValue().username());
        assertEquals("pool-de-prueba", borrado.getValue().userPoolId());
        verify(cognito, never()).adminAddUserToGroup(any(AdminAddUserToGroupRequest.class));
        verifyNoInteractions(usuarioRepository);
    }

    @Test
    @DisplayName("Si el grupo USER no existe en Cognito se propaga ResourceNotFound y no se guarda en BD")
    void registrar_grupoInexistente_propagaYCompensa() {
        cognitoRegistraOk();
        ResourceNotFoundException sinGrupo = ResourceNotFoundException.builder().message("Group not found").build();
        when(cognito.adminAddUserToGroup(any(AdminAddUserToGroupRequest.class))).thenThrow(sinGrupo);

        assertSame(sinGrupo, assertThrows(ResourceNotFoundException.class,
                () -> service.registrar(EMAIL, PASSWORD, NOMBRE)));

        verify(cognito).adminDeleteUser(any(AdminDeleteUserRequest.class));
        verifyNoInteractions(usuarioRepository);
    }

    @Test
    @DisplayName("Si falla PostgreSQL tras crear el usuario en Cognito, el fallo no se oculta y se revierte")
    void registrar_falloEnBaseDeDatos_propagaYCompensa() {
        cognitoRegistraOk();
        DataIntegrityViolationException falloBd = new DataIntegrityViolationException("email duplicado");
        when(usuarioRepository.save(any(Usuario.class))).thenThrow(falloBd);

        assertSame(falloBd, assertThrows(DataIntegrityViolationException.class,
                () -> service.registrar(EMAIL, PASSWORD, NOMBRE)));

        verify(cognito).adminDeleteUser(any(AdminDeleteUserRequest.class));
    }

    @Test
    @DisplayName("Si la compensación también falla, se conserva la excepción original")
    void registrar_falloDeCompensacion_noOcultaLaCausaOriginal() {
        cognitoRegistraOk();
        RuntimeException falloBd = new IllegalStateException("fallo de base de datos");
        when(usuarioRepository.save(any(Usuario.class))).thenThrow(falloBd);
        when(cognito.adminDeleteUser(any(AdminDeleteUserRequest.class))).thenThrow(accesoDenegado());

        assertSame(falloBd, assertThrows(IllegalStateException.class,
                () -> service.registrar(EMAIL, PASSWORD, NOMBRE)));

        verify(cognito).adminDeleteUser(any(AdminDeleteUserRequest.class));
    }

    // ------------------------------------------------------------------ login

    @Test
    @DisplayName("Login: usa USER_PASSWORD_AUTH y devuelve el resultado de Cognito sin transformarlo")
    void login_devuelveElResultadoDeCognito() {
        AuthenticationResultType tokens = AuthenticationResultType.builder()
                .accessToken("acceso-ficticio").idToken("id-ficticio").refreshToken("refresco-ficticio")
                .expiresIn(3600).build();
        when(cognito.initiateAuth(any(InitiateAuthRequest.class)))
                .thenReturn(InitiateAuthResponse.builder().authenticationResult(tokens).build());

        AuthenticationResultType resultado = service.login(EMAIL, PASSWORD);

        assertSame(tokens, resultado);
        ArgumentCaptor<InitiateAuthRequest> peticion = ArgumentCaptor.forClass(InitiateAuthRequest.class);
        verify(cognito).initiateAuth(peticion.capture());
        assertEquals(AuthFlowType.USER_PASSWORD_AUTH, peticion.getValue().authFlow());
        assertEquals(EMAIL, peticion.getValue().authParameters().get("USERNAME"));
        assertEquals("client-de-prueba", peticion.getValue().clientId());
    }

    @Test
    @DisplayName("Login: un reto de Cognito no produce NullPointerException sino AuthChallengeException")
    void login_conReto_lanzaAuthChallengeException() {
        when(cognito.initiateAuth(any(InitiateAuthRequest.class)))
                .thenReturn(InitiateAuthResponse.builder().challengeName(ChallengeNameType.NEW_PASSWORD_REQUIRED).build());

        AuthChallengeException ex = assertThrows(AuthChallengeException.class, () -> service.login(EMAIL, PASSWORD));

        assertEquals("NEW_PASSWORD_REQUIRED", ex.getChallengeName());
    }

    @Test
    @DisplayName("Login: los errores de Cognito se propagan para que el manejador los traduzca")
    void login_propagaUsuarioNoConfirmado() {
        UserNotConfirmedException sinConfirmar = UserNotConfirmedException.builder().message("no confirmado").build();
        when(cognito.initiateAuth(any(InitiateAuthRequest.class))).thenThrow(sinConfirmar);

        assertSame(sinConfirmar, assertThrows(UserNotConfirmedException.class,
                () -> service.login(EMAIL, PASSWORD)));
    }

    // ------------------------------------------------------------------ refresh

    private static InitiateAuthRequest capturarInitiateAuth(CognitoIdentityProviderClient cognito) {
        ArgumentCaptor<InitiateAuthRequest> captor = ArgumentCaptor.forClass(InitiateAuthRequest.class);
        verify(cognito).initiateAuth(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("Refresh: usa REFRESH_TOKEN_AUTH y devuelve el resultado de Cognito sin transformarlo")
    void refrescarTokens_usaRefreshTokenAuth() {
        AuthenticationResultType esperado = AuthenticationResultType.builder()
                .accessToken("acceso-nuevo").idToken("id-nuevo").expiresIn(3600).build();
        when(cognito.initiateAuth(any(InitiateAuthRequest.class)))
                .thenReturn(InitiateAuthResponse.builder().authenticationResult(esperado).build());

        AuthenticationResultType obtenido = service.refrescarTokens("refresco-ficticio", EMAIL);

        assertSame(esperado, obtenido);
        InitiateAuthRequest enviado = capturarInitiateAuth(cognito);
        assertEquals(AuthFlowType.REFRESH_TOKEN_AUTH, enviado.authFlow());
        assertEquals("refresco-ficticio", enviado.authParameters().get("REFRESH_TOKEN"));
        // Sin client secret configurado no se debe enviar SECRET_HASH.
        assertFalse(enviado.authParameters().containsKey("SECRET_HASH"));
    }

    @Test
    @DisplayName("Refresh: con client secret se envía SECRET_HASH calculado con el username")
    void refrescarTokens_conClientSecret_incluyeSecretHash() {
        ReflectionTestUtils.setField(service, "clientSecret", "secreto-de-prueba");
        when(cognito.initiateAuth(any(InitiateAuthRequest.class)))
                .thenReturn(InitiateAuthResponse.builder()
                        .authenticationResult(AuthenticationResultType.builder().accessToken("a").build())
                        .build());

        service.refrescarTokens("refresco-ficticio", EMAIL);

        InitiateAuthRequest enviado = capturarInitiateAuth(cognito);
        assertTrue(enviado.authParameters().containsKey("SECRET_HASH"));
        assertFalse(enviado.authParameters().get("SECRET_HASH").isBlank());
    }

    @Test
    @DisplayName("Refresh: con client secret y sin username falla antes de llamar a Cognito")
    void refrescarTokens_conClientSecretSinUsername_noLlamaACognito() {
        ReflectionTestUtils.setField(service, "clientSecret", "secreto-de-prueba");

        assertThrows(RefreshUsernameRequiredException.class,
                () -> service.refrescarTokens("refresco-ficticio", null));
        assertThrows(RefreshUsernameRequiredException.class,
                () -> service.refrescarTokens("refresco-ficticio", "   "));

        verifyNoInteractions(cognito);
    }

    @Test
    @DisplayName("Refresh: un reto de Cognito no produce NullPointerException sino AuthChallengeException")
    void refrescarTokens_conReto_lanzaAuthChallengeException() {
        when(cognito.initiateAuth(any(InitiateAuthRequest.class)))
                .thenReturn(InitiateAuthResponse.builder()
                        .challengeName(ChallengeNameType.MFA_SETUP).build());

        AuthChallengeException ex = assertThrows(AuthChallengeException.class,
                () -> service.refrescarTokens("refresco-ficticio", EMAIL));

        assertEquals("MFA_SETUP", ex.getChallengeName());
    }

    @Test
    @DisplayName("Refresh: un refresh token caducado se propaga tal cual para que lo traduzca el manejador")
    void refrescarTokens_propagaNotAuthorized() {
        NotAuthorizedException caducado = NotAuthorizedException.builder().message("refresh token caducado").build();
        when(cognito.initiateAuth(any(InitiateAuthRequest.class))).thenThrow(caducado);

        assertSame(caducado, assertThrows(NotAuthorizedException.class,
                () -> service.refrescarTokens("caducado", EMAIL)));
    }
}
