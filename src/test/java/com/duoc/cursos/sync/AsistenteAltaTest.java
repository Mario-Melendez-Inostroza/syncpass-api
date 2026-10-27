package com.duoc.cursos.sync;

import com.duoc.cursos.model.Asistente;
import com.duoc.cursos.model.Evento;
import com.duoc.cursos.model.enums.EstadoInscripcion;
import com.duoc.cursos.model.enums.TipoParticipante;
import com.duoc.cursos.repository.AsistenciaRepository;
import com.duoc.cursos.repository.AsistenteRepository;
import com.duoc.cursos.repository.EncuestaRepository;
import com.duoc.cursos.repository.EventoRepository;
import com.duoc.cursos.repository.ProyectoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Alta de asistentes en terreno (caso §3.1): HTTP -> seguridad -> servicio real -> H2.
 * Fija el contrato que usa la app: lo que el servidor decide (código, inscripción) y la idempotencia por uuid.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AsistenteAltaTest {

    private static final String TOKEN = "tok-monitor";
    private static final String UUID_1 = "9f1c7e62-aaaa-4bbb-8ccc-0123456789ab";
    private static final String UUID_2 = "a7b2c3d4-1111-4222-8333-ba9876543210";
    private static final String CODIGO_1 = "TER-9F1C7E62AAAA";

    @Autowired private MockMvc mvc;
    @Autowired private ProyectoRepository proyectos;
    @Autowired private EventoRepository eventos;
    @Autowired private AsistenteRepository asistentes;
    @Autowired private AsistenciaRepository asistencias;
    @Autowired private EncuestaRepository encuestas;

    @MockitoBean private JwtDecoder jwtDecoder;

    private Evento evento;

    @BeforeEach
    void setUp() {
        encuestas.deleteAll();
        asistencias.deleteAll();
        asistentes.deleteAll();
        eventos.deleteAll();
        proyectos.deleteAll();
        evento = eventos.save(SyncFixtures.evento(proyectos.save(SyncFixtures.proyecto())));
        when(jwtDecoder.decode(TOKEN)).thenReturn(TestJwts.accessToken("sub-monitor", "MONITOR"));
    }

    private ResultActions postAsistentes(String json) throws Exception {
        return mvc.perform(post("/api/sync/asistentes").header("Authorization", "Bearer " + TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String lote(String... items) {
        return "{\"registros\":[" + String.join(",", items) + "]}";
    }

    private static String item(String uuid, String tipo) {
        return "{\"uuid\":\"" + uuid + "\",\"tipo\":\"" + tipo + "\"}";
    }

    @Test
    @DisplayName("alta: el servidor asigna id, código TER y SIN_INSCRIPCION")
    void altaFeliz() throws Exception {
        postAsistentes(lote(item(UUID_1, "VECINO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.aceptados").value(1))
                .andExpect(jsonPath("$.data.resultados[0].estado").value("ACEPTADO"))
                .andExpect(jsonPath("$.data.resultados[0].uuid").value(UUID_1))
                .andExpect(jsonPath("$.data.resultados[0].codigo").value(CODIGO_1))
                .andExpect(jsonPath("$.data.resultados[0].id").isNumber());

        Asistente guardado = asistentes.findByUuid(UUID_1).orElseThrow();
        assertEquals(CODIGO_1, guardado.getCodigo());
        assertEquals(TipoParticipante.VECINO, guardado.getTipo());
        assertEquals(EstadoInscripcion.SIN_INSCRIPCION, guardado.getEstadoInscripcion());
    }

    @Test
    @DisplayName("el cliente no decide el código ni la inscripción: se ignoran si vienen en el cuerpo")
    void clienteNoDecideCodigoNiInscripcion() throws Exception {
        String conIntentoDeInyeccion = "{\"uuid\":\"" + UUID_1 + "\",\"tipo\":\"DOCENTE\","
                + "\"codigo\":\"ASI-0001\",\"estadoInscripcion\":\"INSCRITO\"}";

        postAsistentes(lote(conIntentoDeInyeccion)).andExpect(status().isOk());

        Asistente guardado = asistentes.findByUuid(UUID_1).orElseThrow();
        assertEquals(CODIGO_1, guardado.getCodigo());
        assertEquals(EstadoInscripcion.SIN_INSCRIPCION, guardado.getEstadoInscripcion());
    }

    @Test
    @DisplayName("reenviar el mismo uuid es inocuo: DUPLICADO con el mismo id y código, sin fila nueva")
    void reenvioEsIdempotente() throws Exception {
        postAsistentes(lote(item(UUID_1, "ESTUDIANTE"))).andExpect(status().isOk());
        long antes = asistentes.count();
        Long id = asistentes.findByUuid(UUID_1).orElseThrow().getId();

        postAsistentes(lote(item(UUID_1, "ESTUDIANTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.duplicados").value(1))
                .andExpect(jsonPath("$.data.resultados[0].estado").value("DUPLICADO"))
                .andExpect(jsonPath("$.data.resultados[0].motivo").value("UUID_YA_REGISTRADO"))
                .andExpect(jsonPath("$.data.resultados[0].id").value(id))
                .andExpect(jsonPath("$.data.resultados[0].codigo").value(CODIGO_1));

        assertEquals(antes, asistentes.count());
    }

    @Test
    @DisplayName("lote mixto: un ítem inválido no tumba a los válidos")
    void loteMixto() throws Exception {
        postAsistentes(lote(item(UUID_1, "VECINO"), item("no-es-un-uuid", "VECINO"),
                "{\"uuid\":\"" + UUID_2 + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aceptados").value(1))
                .andExpect(jsonPath("$.data.rechazados").value(2))
                .andExpect(jsonPath("$.data.resultados[1].motivo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.data.resultados[1].detalle").value("uuid"))
                .andExpect(jsonPath("$.data.resultados[2].detalle").value("tipo"));

        assertEquals(1, asistentes.count());
    }

    @Test
    @DisplayName("un tipo que no existe hace ilegible el JSON: 400 y no se guarda nada")
    void tipoDesconocidoEsUn400() throws Exception {
        postAsistentes(lote(item(UUID_1, "MARCIANO"))).andExpect(status().isBadRequest());
        assertEquals(0, asistentes.count());
    }

    @Test
    @DisplayName("lote vacío: 400 VALIDATION_ERROR")
    void loteVacio() throws Exception {
        postAsistentes("{\"registros\":[]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("sin token: 401")
    void sinToken() throws Exception {
        mvc.perform(post("/api/sync/asistentes").contentType(MediaType.APPLICATION_JSON)
                .content(lote(item(UUID_1, "VECINO")))).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("el asistente nuevo llega a los demás teléfonos por el catálogo")
    void apareceEnElCatalogo() throws Exception {
        postAsistentes(lote(item(UUID_1, "VECINO"))).andExpect(status().isOk());

        mvc.perform(get("/api/sync/catalogo").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.asistentes[*].codigo", hasItem(CODIGO_1)));
    }

    @Test
    @DisplayName("con el id que devuelve el alta se puede marcar asistencia (el flujo completo de la app)")
    void altaYLuegoMarcacion() throws Exception {
        postAsistentes(lote(item(UUID_1, "VECINO"))).andExpect(status().isOk());
        Long id = asistentes.findByUuid(UUID_1).orElseThrow().getId();

        String marcacion = "{\"registros\":[{\"uuid\":\"11111111-2222-3333-4444-555555555555\",\"eventoId\":"
                + evento.getId() + ",\"asistenteId\":" + id + ",\"marcadoEn\":\"2026-10-28T15:04:11Z\"}]}";
        mvc.perform(post("/api/sync/asistencias").header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(marcacion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resultados[0].estado").value("ACEPTADO"));
    }
}
