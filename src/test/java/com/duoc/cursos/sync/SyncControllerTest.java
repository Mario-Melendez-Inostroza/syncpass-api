package com.duoc.cursos.sync;

import com.duoc.cursos.model.Asistente;
import com.duoc.cursos.model.Evento;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato JSON de punta a punta: HTTP -> seguridad -> servicio real -> H2.
 *
 * Fija los nombres de campo y los códigos que el móvil implementará en la Fase 3. Si una de estas
 * pruebas cambia, el contrato con la app cambió.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SyncControllerTest {

    private static final String TOKEN = "tok-monitor";

    @Autowired private MockMvc mvc;
    @Autowired private ProyectoRepository proyectos;
    @Autowired private EventoRepository eventos;
    @Autowired private AsistenteRepository asistentes;
    @Autowired private AsistenciaRepository asistencias;
    @Autowired private EncuestaRepository encuestas;

    @MockitoBean private JwtDecoder jwtDecoder;

    private Evento evento;
    private Asistente a1;
    private Asistente a2;

    @BeforeEach
    void setUp() {
        encuestas.deleteAll();
        asistencias.deleteAll();
        asistentes.deleteAll();
        eventos.deleteAll();
        proyectos.deleteAll();

        evento = eventos.save(SyncFixtures.evento(proyectos.save(SyncFixtures.proyecto())));
        a1 = asistentes.save(SyncFixtures.asistente("ASI-0001"));
        a2 = asistentes.save(SyncFixtures.asistente("ASI-0002"));

        when(jwtDecoder.decode(TOKEN)).thenReturn(TestJwts.accessToken("sub-monitor", "MONITOR"));
        when(jwtDecoder.decode("tok-coord")).thenReturn(TestJwts.accessToken("sub-coord", "COORDINADOR"));
    }

    private ResultActions postAsistencias(String json) throws Exception {
        return mvc.perform(post("/api/sync/asistencias").header("Authorization", "Bearer " + TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions postEncuestas(String json) throws Exception {
        return mvc.perform(post("/api/sync/encuestas").header("Authorization", "Bearer " + TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String asistencia(String uuid, Long asistenteId, String coordenadas) {
        return "{\"uuid\":\"" + uuid + "\",\"eventoId\":" + evento.getId() + ",\"asistenteId\":" + asistenteId
                + ",\"marcadoEn\":\"2026-10-28T15:04:11Z\"" + coordenadas + "}";
    }

    private static final String CERCA = ",\"latitud\":-33.5122,\"longitud\":-70.6001";

    // ------------------------------------------------------------------ asistencias

    @Test
    @DisplayName("POST /api/sync/asistencias: envoltorio ApiResponse y resultado por registro")
    void asistencias_contratoDeRespuesta() throws Exception {
        postAsistencias("{\"registros\":[" + asistencia("u-1", a1.getId(), CERCA) + "]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Lote procesado"))
                .andExpect(jsonPath("$.data.aceptados").value(1))
                .andExpect(jsonPath("$.data.duplicados").value(0))
                .andExpect(jsonPath("$.data.rechazados").value(0))
                .andExpect(jsonPath("$.data.resultados[0].indice").value(0))
                .andExpect(jsonPath("$.data.resultados[0].uuid").value("u-1"))
                .andExpect(jsonPath("$.data.resultados[0].estado").value("ACEPTADO"))
                .andExpect(jsonPath("$.data.resultados[0].estadoValidacion").value("VALIDA"))
                .andExpect(jsonPath("$.data.resultados[0].distanciaMetros").isNumber())
                .andExpect(jsonPath("$.data.resultados[0].id").isNumber());

        assertEquals("sub-monitor", asistencias.findByUuid("u-1").orElseThrow().getRegistradoPorSub());
    }

    @Test
    @DisplayName("Reintentar el mismo lote por HTTP: 200 con DUPLICADO y sin filas nuevas")
    void asistencias_reintentoPorHttp() throws Exception {
        String lote = "{\"registros\":[" + asistencia("u-1", a1.getId(), "") + "]}";

        postAsistencias(lote).andExpect(jsonPath("$.data.aceptados").value(1));
        postAsistencias(lote)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aceptados").value(0))
                .andExpect(jsonPath("$.data.duplicados").value(1))
                .andExpect(jsonPath("$.data.resultados[0].estado").value("DUPLICADO"))
                .andExpect(jsonPath("$.data.resultados[0].motivo").value("UUID_YA_REGISTRADO"));

        assertEquals(1, asistencias.count());
    }

    @Test
    @DisplayName("Lote con éxitos y fallos parciales: 200, cada registro con su resultado, los válidos persisten")
    void asistencias_loteParcial() throws Exception {
        String lote = "{\"registros\":["
                + asistencia("u-ok", a1.getId(), CERCA) + ","
                + "{\"uuid\":\"u-lat\",\"eventoId\":" + evento.getId() + ",\"asistenteId\":" + a2.getId()
                + ",\"marcadoEn\":\"2026-10-28T15:04:11Z\",\"latitud\":95,\"longitud\":0},"
                + "{\"uuid\":\"u-sin-evento\",\"eventoId\":999999,\"asistenteId\":" + a2.getId()
                + ",\"marcadoEn\":\"2026-10-28T15:04:11Z\"}"
                + "]}";

        postAsistencias(lote)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aceptados").value(1))
                .andExpect(jsonPath("$.data.rechazados").value(2))
                .andExpect(jsonPath("$.data.resultados[0].estado").value("ACEPTADO"))
                .andExpect(jsonPath("$.data.resultados[1].estado").value("RECHAZADO"))
                .andExpect(jsonPath("$.data.resultados[1].motivo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.data.resultados[1].detalle").value("latitud"))
                .andExpect(jsonPath("$.data.resultados[2].motivo").value("EVENTO_NO_ENCONTRADO"));

        assertEquals(1, asistencias.count());
    }

    @Test
    @DisplayName("Un lote mal formado sí es un error de la petición: 400 con el ErrorResponse de siempre")
    void asistencias_envoltorioInvalido() throws Exception {
        postAsistencias("{\"registros\":[]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detalles.registros").exists());
    }

    // ------------------------------------------------------------------ encuestas

    @Test
    @DisplayName("POST /api/sync/encuestas: encuesta aceptada, duplicada y transitoria, con sus motivos")
    void encuestas_contrato() throws Exception {
        postAsistencias("{\"registros\":[" + asistencia("u-1", a1.getId(), "") + "]}");

        String lote = "{\"registros\":["
                + "{\"uuid\":\"e-1\",\"asistenciaUuid\":\"u-1\",\"satisfaccion\":5,\"utilidad\":4,"
                + "\"comentarios\":\"Muy útil\",\"enviadaEn\":\"2026-10-28T16:10:00Z\"},"
                + "{\"uuid\":\"e-2\",\"asistenciaUuid\":\"u-desconocida\",\"satisfaccion\":3,\"utilidad\":3,"
                + "\"enviadaEn\":\"2026-10-28T16:11:00Z\"},"
                + "{\"uuid\":\"e-3\",\"asistenciaUuid\":\"u-1\",\"satisfaccion\":9,\"utilidad\":3,"
                + "\"enviadaEn\":\"2026-10-28T16:12:00Z\"}"
                + "]}";

        postEncuestas(lote)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resultados[0].estado").value("ACEPTADO"))
                .andExpect(jsonPath("$.data.resultados[1].estado").value("RECHAZADO"))
                .andExpect(jsonPath("$.data.resultados[1].motivo").value("ASISTENCIA_NO_ENCONTRADA"))
                .andExpect(jsonPath("$.data.resultados[2].motivo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.data.resultados[2].detalle").value("satisfaccion"));

        postEncuestas("{\"registros\":[{\"uuid\":\"e-otra\",\"asistenciaUuid\":\"u-1\",\"satisfaccion\":1,"
                + "\"utilidad\":1,\"enviadaEn\":\"2026-10-28T16:20:00Z\"}]}")
                .andExpect(jsonPath("$.data.resultados[0].estado").value("DUPLICADO"))
                .andExpect(jsonPath("$.data.resultados[0].motivo").value("ENCUESTA_YA_REGISTRADA"));

        assertEquals(1, encuestas.count());
    }

    // ------------------------------------------------------------------ catálogo

    @Test
    @DisplayName("GET /api/sync/catalogo: nombres de campo del contrato, sin datos personales")
    void catalogo_contrato() throws Exception {
        mvc.perform(get("/api/sync/catalogo").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.servidorEn").exists())
                .andExpect(jsonPath("$.data.proyectos[0].titulo").value("Alfabetización digital"))
                .andExpect(jsonPath("$.data.proyectos[0].estado").value("EN_CURSO"))
                .andExpect(jsonPath("$.data.proyectos[0].fechaInicio").value("2026-08-01"))
                .andExpect(jsonPath("$.data.eventos[0].proyectoId").isNumber())
                .andExpect(jsonPath("$.data.eventos[0].latitud").value(SyncFixtures.LAT_RECINTO))
                .andExpect(jsonPath("$.data.eventos[0].radioMetros").value(200))
                .andExpect(jsonPath("$.data.asistentes.length()").value(2))
                .andExpect(jsonPath("$.data.asistentes[0].codigo").exists())
                .andExpect(jsonPath("$.data.asistentes[0].nombre").doesNotExist())
                .andExpect(jsonPath("$.data.asistentes[0].email").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/sync/catalogo?desde=<futuro>: listas vacías pero con servidorEn")
    void catalogo_incrementalVacio() throws Exception {
        mvc.perform(get("/api/sync/catalogo").param("desde", "2099-01-01T00:00:00Z")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.proyectos.length()").value(0))
                .andExpect(jsonPath("$.data.eventos.length()").value(0))
                .andExpect(jsonPath("$.data.asistentes.length()").value(0))
                .andExpect(jsonPath("$.data.servidorEn").exists());
    }

    // ------------------------------------------------------------------ resumen

    @Test
    @DisplayName("GET /api/eventos/{id}/resumen: contrato del quórum, y 404 con el ErrorResponse si no existe")
    void resumen_contrato() throws Exception {
        postAsistencias("{\"registros\":[" + asistencia("u-1", a1.getId(), CERCA) + ","
                + asistencia("u-2", a2.getId(), "") + "]}");

        mvc.perform(get("/api/eventos/" + evento.getId() + "/resumen").header("Authorization", "Bearer tok-coord"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.eventoId").value(evento.getId()))
                .andExpect(jsonPath("$.data.totalMarcados").value(2))
                .andExpect(jsonPath("$.data.validas").value(1))
                .andExpect(jsonPath("$.data.sinUbicacion").value(1))
                .andExpect(jsonPath("$.data.fueraDeRadio").value(0))
                .andExpect(jsonPath("$.data.encuestasRecibidas").value(0))
                .andExpect(jsonPath("$.data.satisfaccionPromedio").doesNotExist() );

        mvc.perform(get("/api/eventos/999999/resumen").header("Authorization", "Bearer tok-coord"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }
}
