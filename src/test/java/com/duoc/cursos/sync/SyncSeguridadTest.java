package com.duoc.cursos.sync;

import com.duoc.cursos.model.Usuario;
import com.duoc.cursos.notification.dto.NotificationResponse;
import com.duoc.cursos.notification.service.NotificationService;
import com.duoc.cursos.service.AuthService;
import com.duoc.cursos.sync.dto.AsistenciaSyncItem;
import com.duoc.cursos.sync.service.SyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthenticationResultType;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Autenticación (401) y autorización (403) con la cadena de filtros completa.
 *
 * El JwtDecoder está sustituido por un doble que reconoce unos pocos tokens ficticios: así se ejercitan
 * el converter de cognito:groups, los matchers y el manejo de 401/403 de verdad, sin User Pool ni red.
 * Los servicios de negocio también están sustituidos: aquí solo importa quién puede llegar a ellos.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SyncSeguridadTest {

    private static final String BODY_ASISTENCIAS = "{\"registros\":[{\"uuid\":\"u-1\",\"eventoId\":1,"
            + "\"asistenteId\":1,\"marcadoEn\":\"2026-10-28T15:04:11Z\"}]}";
    private static final String BODY_ENCUESTAS = "{\"registros\":[{\"uuid\":\"e-1\",\"asistenciaUuid\":\"u-1\","
            + "\"satisfaccion\":5,\"utilidad\":4,\"enviadaEn\":\"2026-10-28T16:10:00Z\"}]}";
    private static final String BODY_NOTIFICACION = "{\"email\":\"usuario.prueba@example.com\",\"message\":\"Hola\"}";

    @Autowired private MockMvc mvc;

    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private SyncService syncService;
    @MockitoBean private AuthService authService;
    @MockitoBean private NotificationService notificationService;

    @BeforeEach
    void tokensFicticios() {
        when(jwtDecoder.decode("tok-usuario")).thenReturn(TestJwts.accessToken("sub-usuario", "USER"));
        when(jwtDecoder.decode("tok-sin-grupos")).thenReturn(TestJwts.accessToken("sub-sin-grupos"));
        when(jwtDecoder.decode("tok-coordinador")).thenReturn(TestJwts.accessToken("sub-coord", "COORDINADOR"));
        when(jwtDecoder.decode("tok-coordinador-minusculas")).thenReturn(TestJwts.accessToken("sub-coord2", "coordinador"));
        when(jwtDecoder.decode("tok-admin")).thenReturn(TestJwts.accessToken("sub-admin", "ADMINISTRADOR"));
        when(jwtDecoder.decode("tok-expirado")).thenThrow(new BadJwtException("Jwt expired"));
        when(jwtDecoder.decode("tok-firma-invalida")).thenThrow(new BadJwtException("Signed JWT rejected: Invalid signature"));
    }

    private static MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder req, String token) {
        return token == null ? req : req.header("Authorization", "Bearer " + token);
    }

    private ResultActions llamar(MockHttpServletRequestBuilder req, String token) throws Exception {
        return mvc.perform(conToken(req, token));
    }

    private static MockHttpServletRequestBuilder postJson(String url, String body) {
        return post(url).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    // ------------------------------------------------------------------ 401: sin token / token malo

    @Test
    @DisplayName("Sin token, todos los endpoints protegidos responden 401 con WWW-Authenticate")
    void sinToken_401() throws Exception {
        llamar(get("/api/sync/catalogo"), null).andExpect(status().isUnauthorized()).andExpect(header().exists("WWW-Authenticate"));
        llamar(postJson("/api/sync/asistencias", BODY_ASISTENCIAS), null).andExpect(status().isUnauthorized());
        llamar(postJson("/api/sync/encuestas", BODY_ENCUESTAS), null).andExpect(status().isUnauthorized());
        llamar(get("/api/eventos/1/resumen"), null).andExpect(status().isUnauthorized());
        llamar(postJson("/api/notificar", BODY_NOTIFICACION), null).andExpect(status().isUnauthorized());

        verifyNoInteractions(syncService, notificationService);
    }

    @Test
    @DisplayName("Token caducado -> 401 y no se llega al servicio")
    void tokenCaducado_401() throws Exception {
        llamar(get("/api/sync/catalogo"), "tok-expirado").andExpect(status().isUnauthorized());
        llamar(postJson("/api/sync/asistencias", BODY_ASISTENCIAS), "tok-expirado").andExpect(status().isUnauthorized());

        verifyNoInteractions(syncService);
    }

    @Test
    @DisplayName("Token con firma inválida -> 401 y no se llega al servicio")
    void firmaInvalida_401() throws Exception {
        llamar(postJson("/api/sync/asistencias", BODY_ASISTENCIAS), "tok-firma-invalida").andExpect(status().isUnauthorized());
        llamar(postJson("/api/notificar", BODY_NOTIFICACION), "tok-firma-invalida").andExpect(status().isUnauthorized());

        verifyNoInteractions(syncService, notificationService);
    }

    // ------------------------------------------------------------------ autenticado basta para sincronizar

    @Test
    @DisplayName("Con un token válido y SIN grupos se puede sincronizar (basta con estar autenticado)")
    void tokenValido_puedeSincronizar() throws Exception {
        llamar(get("/api/sync/catalogo"), "tok-sin-grupos").andExpect(status().isOk());
        llamar(postJson("/api/sync/asistencias", BODY_ASISTENCIAS), "tok-sin-grupos").andExpect(status().isOk());
        llamar(postJson("/api/sync/encuestas", BODY_ENCUESTAS), "tok-usuario").andExpect(status().isOk());

        verify(syncService).catalogo(null);
        verify(syncService).sincronizarEncuestas(anyList());
    }

    @Test
    @DisplayName("El usuario que registra sale del 'sub' del token, no del cuerpo")
    void registradoPor_sale_del_token() throws Exception {
        // El cliente intenta colar otro registrador en el cuerpo; el servidor debe ignorarlo.
        String cuerpoConIntento = "{\"registros\":[{\"uuid\":\"u-1\",\"eventoId\":1,\"asistenteId\":1,"
                + "\"marcadoEn\":\"2026-10-28T15:04:11Z\",\"registradoPorSub\":\"sub-de-otra-persona\"}],"
                + "\"registradoPorSub\":\"sub-de-otra-persona\"}";

        llamar(postJson("/api/sync/asistencias", cuerpoConIntento), "tok-usuario").andExpect(status().isOk());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AsistenciaSyncItem>> items = ArgumentCaptor.forClass(List.class);
        verify(syncService).sincronizarAsistencias(items.capture(), eq("sub-usuario"));
        assertEquals(1, items.getValue().size());
        verify(syncService, never()).sincronizarAsistencias(anyList(), eq("sub-de-otra-persona"));
    }

    @Test
    @DisplayName("La API es sin estado: una petición autorizada no crea sesión (sin Set-Cookie)")
    void sinSesion() throws Exception {
        llamar(get("/api/sync/catalogo"), "tok-usuario")
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    // ------------------------------------------------------------------ 403: rol de coordinación

    @Test
    @DisplayName("Resumen: token válido pero sin COORDINADOR/ADMINISTRADOR -> 403")
    void resumen_sinRol_403() throws Exception {
        llamar(get("/api/eventos/1/resumen"), "tok-usuario").andExpect(status().isForbidden());
        llamar(get("/api/eventos/1/resumen"), "tok-sin-grupos").andExpect(status().isForbidden());

        verifyNoInteractions(syncService);
    }

    @Test
    @DisplayName("Resumen: COORDINADOR y ADMINISTRADOR -> 200 (también 'coordinador' en minúsculas)")
    void resumen_conRol_200() throws Exception {
        llamar(get("/api/eventos/1/resumen"), "tok-coordinador").andExpect(status().isOk());
        llamar(get("/api/eventos/1/resumen"), "tok-coordinador-minusculas").andExpect(status().isOk());
        llamar(get("/api/eventos/1/resumen"), "tok-admin").andExpect(status().isOk());

        verify(syncService, times(3)).resumen(1L);
    }

    // ------------------------------------------------------------------ notificaciones

    @Test
    @DisplayName("Notificar: con token válido responde 200 (antes era un relay abierto)")
    void notificar_conToken() throws Exception {
        when(notificationService.sendNotification(any()))
                .thenReturn(NotificationResponse.builder().success(true).topic("t").message("ok").build());

        llamar(postJson("/api/notificar", BODY_NOTIFICACION), "tok-usuario").andExpect(status().isOk());

        verify(notificationService).sendNotification(any());
    }

    @Test
    @DisplayName("El endpoint que revelaba el tópico secreto de un email ya no existe")
    void topicoSecreto_noSeExpone() throws Exception {
        // Autenticado también: no debe existir. (Sin token daría 401 antes de llegar a buscarlo.)
        llamar(get("/api/notificar/topic/usuario.prueba@example.com"), "tok-usuario")
                .andExpect(status().is4xxClientError());
        verify(notificationService, never()).generateTopic(anyString());
    }

    // ------------------------------------------------------------------ rutas públicas y token viejo

    @Test
    @DisplayName("Login ignora un Bearer caducado: un cliente con un token viejo debe poder iniciar sesión")
    void login_ignoraBearerCaducado() throws Exception {
        when(authService.login(anyString(), anyString())).thenReturn(
                AuthenticationResultType.builder().accessToken("a").idToken("i").refreshToken("r").expiresIn(3600).build());

        llamar(postJson("/api/auth/login", "{\"email\":\"usuario.prueba@example.com\",\"password\":\"Password-de-prueba-1!\"}"),
                "tok-expirado")
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Registro y health son públicos, aunque lleguen con un Bearer caducado")
    void rutasPublicas() throws Exception {
        Usuario u = new Usuario();
        u.setId("sub-nuevo");
        u.setEmail("usuario.prueba@example.com");
        u.setNombre("Usuario Prueba");
        u.setRoles(Set.of("USER"));
        when(authService.registrar(anyString(), anyString(), anyString())).thenReturn(u);

        llamar(postJson("/api/auth/registro",
                "{\"email\":\"usuario.prueba@example.com\",\"password\":\"Password-de-prueba-1!\",\"nombre\":\"Usuario Prueba\"}"),
                null).andExpect(status().isOk());
        llamar(get("/api/health"), null).andExpect(status().isOk());
        llamar(get("/api/health"), "tok-expirado").andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ validación del lote (envoltorio)

    @Test
    @DisplayName("Lote vacío, sin 'registros' o mal formado -> 400; un lote que cabe pasa")
    void envoltorioInvalido_400() throws Exception {
        llamar(postJson("/api/sync/asistencias", "{\"registros\":[]}"), "tok-usuario").andExpect(status().isBadRequest());
        llamar(postJson("/api/sync/asistencias", "{}"), "tok-usuario").andExpect(status().isBadRequest());
        llamar(postJson("/api/sync/asistencias", "esto no es json"), "tok-usuario").andExpect(status().isBadRequest());
        llamar(postJson("/api/sync/encuestas", "{\"registros\":[]}"), "tok-usuario").andExpect(status().isBadRequest());

        verifyNoInteractions(syncService);
    }

    @Test
    @DisplayName("Más de 500 registros en un lote -> 400 (techo contra lotes gigantes)")
    void loteDemasiadoGrande_400() throws Exception {
        String item = "{\"uuid\":\"u\",\"eventoId\":1,\"asistenteId\":1,\"marcadoEn\":\"2026-10-28T15:04:11Z\"}";
        String cuerpo = "{\"registros\":[" + String.join(",", java.util.Collections.nCopies(501, item)) + "]}";

        llamar(postJson("/api/sync/asistencias", cuerpo), "tok-usuario").andExpect(status().isBadRequest());

        verifyNoInteractions(syncService);
    }

    @Test
    @DisplayName("?desde= con un formato que no es ISO-8601 -> 400, no 500")
    void desdeMalFormado_400() throws Exception {
        llamar(get("/api/sync/catalogo").param("desde", "ayer"), "tok-usuario").andExpect(status().isBadRequest());
    }
}
