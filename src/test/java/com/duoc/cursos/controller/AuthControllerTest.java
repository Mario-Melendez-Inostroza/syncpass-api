package com.duoc.cursos.controller;

import com.duoc.cursos.exception.AuthChallengeException;
import com.duoc.cursos.exception.GlobalExceptionHandler;
import com.duoc.cursos.model.Usuario;
import com.duoc.cursos.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas del contrato HTTP de registro y login junto con GlobalExceptionHandler.
 * AuthService está mockeado: no hay Cognito, base de datos ni credenciales.
 * (MockMvc standalone: no carga el contexto de Spring, por lo que no prueba SecurityConfig.)
 */
class AuthControllerTest {

    private static final String SECRETO_INTERNO = "DETALLE-INTERNO-DE-AWS-NO-DEBE-SALIR";

    private AuthService authService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private ResultActions registrar(String json) throws Exception {
        return mvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions login(String json) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static final String REGISTRO_VALIDO =
            "{\"email\":\"usuario.prueba@example.com\",\"password\":\"Password-de-prueba-1!\",\"nombre\":\"Usuario Prueba\"}";
    private static final String LOGIN_VALIDO =
            "{\"email\":\"usuario.prueba@example.com\",\"password\":\"Password-de-prueba-1!\"}";

    private static Usuario usuarioConRolUser() {
        Usuario u = new Usuario();
        u.setId("sub-ficticio-123");
        u.setEmail("usuario.prueba@example.com");
        u.setNombre("Usuario Prueba");
        u.setRoles(Set.of("USER"));
        return u;
    }

    private static void assertSinFuga(ResultActions resultado) throws Exception {
        String cuerpo = resultado.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(cuerpo.contains(SECRETO_INTERNO), "La respuesta filtra un mensaje interno: " + cuerpo);
    }

    // ------------------------------------------------------------------ registro: rol

    @Test
    @DisplayName("Registro: un rol privilegiado enviado por el cliente se ignora; el servicio no recibe roles")
    void registro_ignoraRolesDelCliente() throws Exception {
        when(authService.registrar(anyString(), anyString(), anyString())).thenReturn(usuarioConRolUser());

        registrar("{\"email\":\"usuario.prueba@example.com\",\"password\":\"Password-de-prueba-1!\","
                + "\"nombre\":\"Usuario Prueba\",\"roles\":[\"ADMIN\"]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.length()").value(1))
                .andExpect(jsonPath("$.roles[0]").value("USER"));

        // La firma del servicio ya no admite roles: solo recibe email, password y nombre.
        verify(authService).registrar("usuario.prueba@example.com", "Password-de-prueba-1!", "Usuario Prueba");
    }

    @Test
    @DisplayName("Registro: omitir roles no produce error y mantiene el contrato de respuesta")
    void registro_sinRoles_funcionaYMantieneContrato() throws Exception {
        when(authService.registrar(anyString(), anyString(), anyString())).thenReturn(usuarioConRolUser());

        registrar(REGISTRO_VALIDO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("sub-ficticio-123"))
                .andExpect(jsonPath("$.email").value("usuario.prueba@example.com"))
                .andExpect(jsonPath("$.nombre").value("Usuario Prueba"))
                .andExpect(jsonPath("$.roles[0]").value("USER"));
    }

    // ------------------------------------------------------------------ registro: entrada inválida

    @Test
    @DisplayName("Registro: datos inválidos devuelven 400 VALIDATION_ERROR y no llegan al servicio")
    void registro_datosInvalidos_400() throws Exception {
        registrar("{\"email\":\"no-es-un-email\",\"password\":\"corta\",\"nombre\":\"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detalles.email").exists())
                .andExpect(jsonPath("$.detalles.password").exists())
                .andExpect(jsonPath("$.detalles.nombre").exists());

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("Registro: JSON malformado devuelve 400 y no un 500")
    void registro_jsonMalformado_400() throws Exception {
        registrar("{\"email\": ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("Registro: cuerpo vacío devuelve 400")
    void registro_cuerpoVacio_400() throws Exception {
        mvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    @DisplayName("Un método HTTP no soportado devuelve 405 en vez de 500")
    void metodoNoSoportado_405() throws Exception {
        mvc.perform(get("/api/auth/login"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));
    }

    // ------------------------------------------------------------------ registro: errores de Cognito / BD

    @Test
    @DisplayName("Registro: usuario duplicado en Cognito -> 409")
    void registro_usuarioDuplicado_409() throws Exception {
        when(authService.registrar(anyString(), anyString(), anyString()))
                .thenThrow(UsernameExistsException.builder().message(SECRETO_INTERNO).build());

        ResultActions r = registrar(REGISTRO_VALIDO)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"))
                .andExpect(jsonPath("$.mensaje").value("El email ya está registrado"));
        assertSinFuga(r);
    }

    @Test
    @DisplayName("Registro: email duplicado en PostgreSQL -> 409")
    void registro_emailDuplicadoEnBd_409() throws Exception {
        when(authService.registrar(anyString(), anyString(), anyString()))
                .thenThrow(new DataIntegrityViolationException(SECRETO_INTERNO));

        ResultActions r = registrar(REGISTRO_VALIDO)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));
        assertSinFuga(r);
    }

    @Test
    @DisplayName("Registro: contraseña fuera de la política -> 400 sin el texto interno de AWS")
    void registro_passwordInvalida_400() throws Exception {
        when(authService.registrar(anyString(), anyString(), anyString()))
                .thenThrow(InvalidPasswordException.builder().message(SECRETO_INTERNO).build());

        ResultActions r = registrar(REGISTRO_VALIDO)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
        assertSinFuga(r);
    }

    @Test
    @DisplayName("Registro: parámetro inválido de Cognito -> 400 sin el texto interno de AWS")
    void registro_parametroInvalido_400() throws Exception {
        when(authService.registrar(anyString(), anyString(), anyString()))
                .thenThrow(InvalidParameterException.builder().message(SECRETO_INTERNO).build());

        ResultActions r = registrar(REGISTRO_VALIDO)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Parámetro inválido"));
        assertSinFuga(r);
    }

    @Test
    @DisplayName("Registro: grupo USER inexistente -> 500 CONFIGURATION_ERROR sin detalle interno")
    void registro_grupoInexistente_errorDeConfiguracion() throws Exception {
        when(authService.registrar(anyString(), anyString(), anyString()))
                .thenThrow(ResourceNotFoundException.builder().message(SECRETO_INTERNO).build());

        ResultActions r = registrar(REGISTRO_VALIDO)
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("CONFIGURATION_ERROR"));
        assertSinFuga(r);
    }

    @Test
    @DisplayName("Registro: permisos IAM insuficientes para confirmar -> 502 COGNITO_PERMISSION_DENIED")
    void registro_permisosIamInsuficientes_502() throws Exception {
        when(authService.registrar(anyString(), anyString(), anyString()))
                .thenThrow(CognitoIdentityProviderException.builder()
                        .message(SECRETO_INTERNO)
                        .awsErrorDetails(AwsErrorDetails.builder().errorCode("AccessDeniedException").build())
                        .build());

        ResultActions r = registrar(REGISTRO_VALIDO)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("COGNITO_PERMISSION_DENIED"));
        assertSinFuga(r);
    }

    @Test
    @DisplayName("Registro: otro error de Cognito -> 502 COGNITO_ERROR")
    void registro_otroErrorDeCognito_502() throws Exception {
        when(authService.registrar(anyString(), anyString(), anyString()))
                .thenThrow(CognitoIdentityProviderException.builder()
                        .message(SECRETO_INTERNO)
                        .awsErrorDetails(AwsErrorDetails.builder().errorCode("InternalErrorException").build())
                        .build());

        ResultActions r = registrar(REGISTRO_VALIDO)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("COGNITO_ERROR"));
        assertSinFuga(r);
    }

    @Test
    @DisplayName("Registro: sin credenciales/red hacia AWS -> 503")
    void registro_sinCredencialesAws_503() throws Exception {
        when(authService.registrar(anyString(), anyString(), anyString()))
                .thenThrow(SdkClientException.create(SECRETO_INTERNO));

        ResultActions r = registrar(REGISTRO_VALIDO)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("SERVICE_UNAVAILABLE"));
        assertSinFuga(r);
    }

    @Test
    @DisplayName("Un error inesperado devuelve 500 genérico sin su mensaje")
    void errorInesperado_500SinFuga() throws Exception {
        when(authService.registrar(anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException(SECRETO_INTERNO));

        ResultActions r = registrar(REGISTRO_VALIDO)
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.mensaje").value("Error interno del servidor"));
        assertSinFuga(r);
    }

    // ------------------------------------------------------------------ login

    @Test
    @DisplayName("Login: conserva el contrato {accessToken, idToken, refreshToken, expiresIn}")
    void login_conservaElContratoActual() throws Exception {
        when(authService.login("usuario.prueba@example.com", "Password-de-prueba-1!"))
                .thenReturn(AuthenticationResultType.builder()
                        .accessToken("acceso-ficticio").idToken("id-ficticio")
                        .refreshToken("refresco-ficticio").expiresIn(3600).build());

        login(LOGIN_VALIDO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("acceso-ficticio"))
                .andExpect(jsonPath("$.idToken").value("id-ficticio"))
                .andExpect(jsonPath("$.refreshToken").value("refresco-ficticio"))
                .andExpect(jsonPath("$.expiresIn").value(3600));
    }

    @Test
    @DisplayName("Login: credenciales incorrectas -> 401 'Credenciales inválidas'")
    void login_credencialesIncorrectas_401() throws Exception {
        when(authService.login(anyString(), anyString()))
                .thenThrow(NotAuthorizedException.builder().message(SECRETO_INTERNO).build());

        ResultActions r = login(LOGIN_VALIDO)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.mensaje").value("Credenciales inválidas"));
        assertSinFuga(r);
    }

    @Test
    @DisplayName("Login: usuario no confirmado -> 403 USER_NOT_CONFIRMED")
    void login_usuarioNoConfirmado_403() throws Exception {
        when(authService.login(anyString(), anyString()))
                .thenThrow(UserNotConfirmedException.builder().message(SECRETO_INTERNO).build());

        ResultActions r = login(LOGIN_VALIDO)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("USER_NOT_CONFIRMED"));
        assertSinFuga(r);
    }

    @Test
    @DisplayName("Login: reto de autenticación no soportado -> 403 CHALLENGE_NOT_SUPPORTED")
    void login_conReto_403() throws Exception {
        when(authService.login(anyString(), anyString()))
                .thenThrow(new AuthChallengeException("NEW_PASSWORD_REQUIRED"));

        login(LOGIN_VALIDO)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("CHALLENGE_NOT_SUPPORTED"));
    }

    @Test
    @DisplayName("Login: datos inválidos -> 400 VALIDATION_ERROR")
    void login_datosInvalidos_400() throws Exception {
        login("{\"email\":\"x\",\"password\":\"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));

        verifyNoInteractions(authService);
    }

    // ------------------------------------------------------------------ logout

    @Test
    @DisplayName("Logout sin header Authorization -> 400 (comportamiento existente)")
    void logout_sinHeader_400() throws Exception {
        mvc.perform(post("/api/auth/logout"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("Logout con Bearer -> 204")
    void logout_conBearer_204() throws Exception {
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer token-ficticio"))
                .andExpect(status().isNoContent());

        verify(authService).logout("token-ficticio");
    }
}
