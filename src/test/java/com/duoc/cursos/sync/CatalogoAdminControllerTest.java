package com.duoc.cursos.sync;

import com.duoc.cursos.model.Asistente;
import com.duoc.cursos.model.Evento;
import com.duoc.cursos.model.Proyecto;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Gestión de proyectos y eventos por el Docente / Coordinador(a) (caso §3.1): permisos por rol, validaciones
 * y reglas de borrado. HTTP -> seguridad -> servicio real -> H2.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CatalogoAdminControllerTest {

    private static final String COORD = "tok-coord";
    private static final String MONITOR = "tok-monitor";

    @Autowired private MockMvc mvc;
    @Autowired private ProyectoRepository proyectos;
    @Autowired private EventoRepository eventos;
    @Autowired private AsistenteRepository asistentes;
    @Autowired private AsistenciaRepository asistencias;
    @Autowired private EncuestaRepository encuestas;

    @MockitoBean private JwtDecoder jwtDecoder;

    private Proyecto proyecto;

    @BeforeEach
    void setUp() {
        encuestas.deleteAll();
        asistencias.deleteAll();
        asistentes.deleteAll();
        eventos.deleteAll();
        proyectos.deleteAll();
        proyecto = proyectos.save(SyncFixtures.proyecto());

        when(jwtDecoder.decode(COORD)).thenReturn(TestJwts.accessToken("sub-coord", "COORDINADOR"));
        when(jwtDecoder.decode(MONITOR)).thenReturn(TestJwts.accessToken("sub-monitor", "MONITOR"));
    }

    private ResultActions enviar(MockHttpServletRequestBuilder peticion, String token, String json) throws Exception {
        if (token != null) {
            peticion.header("Authorization", "Bearer " + token);
        }
        if (json != null) {
            peticion.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mvc.perform(peticion);
    }

    private static final String PROYECTO_JSON = "{\"titulo\":\"Huerta comunitaria\",\"descripcion\":\"Proyecto ficticio\","
            + "\"areaTematica\":\"emprendimiento\",\"unidadVecinal\":\"UV-5\",\"fechaInicio\":\"2026-09-01\","
            + "\"fechaTermino\":\"2026-12-01\"}";

    private String eventoJson(Long proyectoId, String extra) {
        return "{\"proyectoId\":" + proyectoId + ",\"nombre\":\"Taller de compostaje\","
                + "\"fechaHoraProgramada\":\"2026-11-20T15:00:00Z\",\"cuposMaximos\":25,\"recinto\":\"Sede ficticia\""
                + extra + "}";
    }

    // ------------------------------------------------------------------ permisos

    @Test
    @DisplayName("sin token: 401; con rol MONITOR: 403, en cada ruta de gestión")
    void soloElCoordinadorGestiona() throws Exception {
        Long id = proyecto.getId();
        String[][] rutas = {{"POST", "/api/proyectos"}, {"PUT", "/api/proyectos/" + id},
                {"DELETE", "/api/proyectos/" + id}, {"POST", "/api/eventos"}, {"PUT", "/api/eventos/1"},
                {"DELETE", "/api/eventos/1"}, {"GET", "/api/eventos/1/asistencias"}};
        for (String[] r : rutas) {
            enviar(construir(r[0], r[1]), null, PROYECTO_JSON).andExpect(status().isUnauthorized());
            enviar(construir(r[0], r[1]), MONITOR, PROYECTO_JSON).andExpect(status().isForbidden());
        }
        assertEquals(1, proyectos.count());
    }

    private static MockHttpServletRequestBuilder construir(String metodo, String ruta) {
        return switch (metodo) {
            case "POST" -> post(ruta);
            case "PUT" -> put(ruta);
            case "DELETE" -> delete(ruta);
            default -> get(ruta);
        };
    }

    // ------------------------------------------------------------------ proyectos

    @Test
    @DisplayName("crear proyecto: 201, estado PLANIFICADO por defecto y aparece en el catálogo")
    void crearProyecto() throws Exception {
        enviar(post("/api/proyectos"), COORD, PROYECTO_JSON)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.titulo").value("Huerta comunitaria"))
                .andExpect(jsonPath("$.data.estado").value("PLANIFICADO"))
                .andExpect(jsonPath("$.data.id").isNumber());

        enviar(get("/api/sync/catalogo"), MONITOR, null)
                .andExpect(jsonPath("$.data.proyectos[*].titulo", hasItem("Huerta comunitaria")));
    }

    @Test
    @DisplayName("proyecto inválido: 400 con el detalle por campo, incluida la coherencia de fechas")
    void proyectoInvalido() throws Exception {
        String malo = "{\"titulo\":\" \",\"areaTematica\":\"x\",\"unidadVecinal\":\"y\","
                + "\"fechaInicio\":\"2026-12-01\",\"fechaTermino\":\"2026-09-01\"}";

        enviar(post("/api/proyectos"), COORD, malo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.detalles.titulo").exists())
                .andExpect(jsonPath("$.detalles.fechasCoherentes").exists());

        assertEquals(1, proyectos.count());
    }

    @Test
    @DisplayName("editar proyecto: cambia los datos; uno que no existe es 404")
    void editarProyecto() throws Exception {
        enviar(put("/api/proyectos/" + proyecto.getId()), COORD, PROYECTO_JSON.replace("PLANIFICADO", "CERRADO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.titulo").value("Huerta comunitaria"));
        assertEquals("Huerta comunitaria", proyectos.findById(proyecto.getId()).orElseThrow().getTitulo());

        enviar(put("/api/proyectos/999999"), COORD, PROYECTO_JSON)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("eliminar proyecto: con eventos es 409 y no se borra; vacío se elimina")
    void eliminarProyecto() throws Exception {
        Evento ev = eventos.save(SyncFixtures.evento(proyecto));

        enviar(delete("/api/proyectos/" + proyecto.getId()), COORD, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));
        assertTrue(proyectos.existsById(proyecto.getId()));

        eventos.delete(ev);
        enviar(delete("/api/proyectos/" + proyecto.getId()), COORD, null).andExpect(status().isOk());
        assertTrue(proyectos.findById(proyecto.getId()).isEmpty());
    }

    // ------------------------------------------------------------------ eventos

    @Test
    @DisplayName("crear evento: 201, radio por defecto 200 y aparece en el catálogo")
    void crearEvento() throws Exception {
        enviar(post("/api/eventos"), COORD, eventoJson(proyecto.getId(), ",\"latitud\":-33.45,\"longitud\":-70.66"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.nombre").value("Taller de compostaje"))
                .andExpect(jsonPath("$.data.radioMetros").value(200))
                .andExpect(jsonPath("$.data.proyectoId").value(proyecto.getId()));

        enviar(get("/api/sync/catalogo"), MONITOR, null)
                .andExpect(jsonPath("$.data.eventos[*].nombre", hasItem("Taller de compostaje")));
    }

    @Test
    @DisplayName("evento de un proyecto que no existe: 404")
    void eventoSinProyecto() throws Exception {
        enviar(post("/api/eventos"), COORD, eventoJson(999999L, "")).andExpect(status().isNotFound());
        assertEquals(0, eventos.count());
    }

    @Test
    @DisplayName("evento inválido: cupos 0, latitud sin longitud y coordenadas fuera de rango son 400")
    void eventoInvalido() throws Exception {
        enviar(post("/api/eventos"), COORD, eventoJson(proyecto.getId(), ",\"latitud\":-33.45")
                .replace("\"cuposMaximos\":25", "\"cuposMaximos\":0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles.cuposMaximos").exists())
                .andExpect(jsonPath("$.detalles.coordenadasCoherentes").exists());

        enviar(post("/api/eventos"), COORD, eventoJson(proyecto.getId(), ",\"latitud\":95,\"longitud\":-70"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles.latitud").exists());

        assertEquals(0, eventos.count());
    }

    @Test
    @DisplayName("editar evento: cambia ubicación y radio")
    void editarEvento() throws Exception {
        Evento ev = eventos.save(SyncFixtures.evento(proyecto));

        enviar(put("/api/eventos/" + ev.getId()), COORD,
                eventoJson(proyecto.getId(), ",\"latitud\":-33.4,\"longitud\":-70.6,\"radioMetros\":350"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.radioMetros").value(350));

        Evento guardado = eventos.findById(ev.getId()).orElseThrow();
        assertEquals(-33.4, guardado.getLatitud());
        assertEquals("Taller de compostaje", guardado.getNombre());
    }

    @Test
    @DisplayName("eliminar evento: con asistencias es 409 (no se pierde evidencia); sin ellas se elimina")
    void eliminarEvento() throws Exception {
        Evento ev = eventos.save(SyncFixtures.evento(proyecto));
        Asistente a = asistentes.save(SyncFixtures.asistente("ASI-0001"));
        asistencias.save(SyncFixtures.asistencia("uuid-asistencia-1", ev, a));

        enviar(delete("/api/eventos/" + ev.getId()), COORD, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));
        assertTrue(eventos.existsById(ev.getId()));

        asistencias.deleteAll();
        enviar(delete("/api/eventos/" + ev.getId()), COORD, null).andExpect(status().isOk());
        assertTrue(eventos.findById(ev.getId()).isEmpty());
    }

    @Test
    @DisplayName("eliminar un evento que no existe: 404")
    void eliminarEventoInexistente() throws Exception {
        enviar(delete("/api/eventos/999999"), COORD, null).andExpect(status().isNotFound());
    }
}
