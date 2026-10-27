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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Gestión de asistentes por el coordinador (caso §3.1 "consulta listas previas de inscritos", §3.2 y §5.1). */
@SpringBootTest
@AutoConfigureMockMvc
class AsistentesGestionTest {

    private static final String COORD = "tok-coord";
    private static final String MONITOR = "tok-monitor";

    @Autowired private MockMvc mvc;
    @Autowired private ProyectoRepository proyectos;
    @Autowired private EventoRepository eventos;
    @Autowired private AsistenteRepository asistentes;
    @Autowired private AsistenciaRepository asistencias;
    @Autowired private EncuestaRepository encuestas;

    @MockitoBean private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        encuestas.deleteAll();
        asistencias.deleteAll();
        asistentes.deleteAll();
        eventos.deleteAll();
        proyectos.deleteAll();
        when(jwtDecoder.decode(COORD)).thenReturn(TestJwts.accessToken("sub-coord", "COORDINADOR"));
        when(jwtDecoder.decode(MONITOR)).thenReturn(TestJwts.accessToken("sub-monitor", "MONITOR"));
    }

    private ResultActions enviar(MockHttpServletRequestBuilder peticion, String token, String json) throws Exception {
        if (token != null) peticion.header("Authorization", "Bearer " + token);
        if (json != null) peticion.contentType(MediaType.APPLICATION_JSON).content(json);
        return mvc.perform(peticion);
    }

    @Test
    @DisplayName("crear un inscrito: el servidor genera el código y queda INSCRITO por defecto")
    void crearUno() throws Exception {
        enviar(post("/api/asistentes"), COORD, "{\"tipo\":\"VECINO\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].codigo").value(org.hamcrest.Matchers.matchesPattern("INS-\\d{6}")))
                .andExpect(jsonPath("$.data[0].tipo").value("VECINO"))
                .andExpect(jsonPath("$.data[0].estadoInscripcion").value("INSCRITO"))
                .andExpect(jsonPath("$.data[0].totalAsistencias").value(0))
                .andExpect(jsonPath("$.data[0].creadoEnTerreno").value(false));

        assertEquals(1, asistentes.count());
    }

    @Test
    @DisplayName("el código se deriva del id del servidor: el cliente no puede imponerlo")
    void elClienteNoImponeElCodigo() throws Exception {
        enviar(post("/api/asistentes"), COORD, "{\"tipo\":\"DOCENTE\",\"codigo\":\"HACK-1\"}").andExpect(status().isCreated());

        Asistente a = asistentes.findAll().get(0);
        assertEquals(String.format("INS-%06d", a.getId()), a.getCodigo());
    }

    @Test
    @DisplayName("crear varios de una vez: todos distintos y con el mismo tipo e inscripción")
    void crearVarios() throws Exception {
        enviar(post("/api/asistentes"), COORD, "{\"tipo\":\"ESTUDIANTE\",\"estadoInscripcion\":\"SIN_INSCRIPCION\",\"cantidad\":25}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.length()").value(25));

        assertEquals(25, asistentes.count());
        assertEquals(25, asistentes.findAll().stream().map(Asistente::getCodigo).distinct().count());
        assertTrue(asistentes.findAll().stream().allMatch(a -> a.getTipo() == TipoParticipante.ESTUDIANTE
                && a.getEstadoInscripcion() == EstadoInscripcion.SIN_INSCRIPCION));
    }

    @Test
    @DisplayName("cantidad fuera de rango (0 o más de 200) y tipo inexistente son 400, y no se crea nada")
    void validaciones() throws Exception {
        enviar(post("/api/asistentes"), COORD, "{\"tipo\":\"VECINO\",\"cantidad\":0}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detalles.cantidad").exists());
        enviar(post("/api/asistentes"), COORD, "{\"tipo\":\"VECINO\",\"cantidad\":201}").andExpect(status().isBadRequest());
        enviar(post("/api/asistentes"), COORD, "{\"tipo\":\"MARCIANO\"}").andExpect(status().isBadRequest());
        enviar(post("/api/asistentes"), COORD, "{}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detalles.tipo").exists());

        assertEquals(0, asistentes.count());
    }

    @Test
    @DisplayName("la lista trae en cuántos eventos fue marcado cada uno y si lo dio de alta un monitor en terreno")
    void listar() throws Exception {
        Evento e = eventos.save(SyncFixtures.evento(proyectos.save(SyncFixtures.proyecto())));
        Asistente a1 = asistentes.save(SyncFixtures.asistente("ASI-0001"));
        Asistente enTerreno = SyncFixtures.asistente("TER-ABCDEF123456");
        enTerreno.setUuid("9f1c7e62-aaaa-4bbb-8ccc-0123456789ab");
        asistentes.save(enTerreno);
        asistencias.save(SyncFixtures.asistencia("u-1", e, a1));

        enviar(get("/api/asistentes"), COORD, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].codigo").value("ASI-0001"))
                .andExpect(jsonPath("$.data[0].totalAsistencias").value(1))
                .andExpect(jsonPath("$.data[0].creadoEnTerreno").value(false))
                .andExpect(jsonPath("$.data[1].codigo").value("TER-ABCDEF123456"))
                .andExpect(jsonPath("$.data[1].totalAsistencias").value(0))
                .andExpect(jsonPath("$.data[1].creadoEnTerreno").value(true));
    }

    @Test
    @DisplayName("la lista no expone datos personales ni el uuid del teléfono")
    void sinDatosPersonales() throws Exception {
        Asistente a = SyncFixtures.asistente("ASI-0001");
        a.setUuid("9f1c7e62-aaaa-4bbb-8ccc-0123456789ab");
        asistentes.save(a);

        enviar(get("/api/asistentes"), COORD, null)
                .andExpect(jsonPath("$.data[0].uuid").doesNotExist())
                .andExpect(jsonPath("$.data[0].nombre").doesNotExist())
                .andExpect(jsonPath("$.data[0].email").doesNotExist());
    }

    @Test
    @DisplayName("editar cambia el tipo y la inscripción pero nunca el código")
    void editar() throws Exception {
        Asistente a = asistentes.save(SyncFixtures.asistente("ASI-0001"));

        enviar(put("/api/asistentes/" + a.getId()), COORD,
                "{\"tipo\":\"DOCENTE\",\"estadoInscripcion\":\"SIN_INSCRIPCION\",\"codigo\":\"OTRO\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tipo").value("DOCENTE"))
                .andExpect(jsonPath("$.data.estadoInscripcion").value("SIN_INSCRIPCION"))
                .andExpect(jsonPath("$.data.codigo").value("ASI-0001"));

        Asistente guardado = asistentes.findById(a.getId()).orElseThrow();
        assertEquals(TipoParticipante.DOCENTE, guardado.getTipo());
        assertEquals("ASI-0001", guardado.getCodigo());
    }

    @Test
    @DisplayName("editar o eliminar un asistente que no existe es 404")
    void noExiste() throws Exception {
        enviar(put("/api/asistentes/999999"), COORD, "{\"tipo\":\"VECINO\",\"estadoInscripcion\":\"INSCRITO\"}")
                .andExpect(status().isNotFound());
        enviar(delete("/api/asistentes/999999"), COORD, null).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("eliminar: con asistencias es 409 (no se pierde evidencia); sin ellas se elimina")
    void eliminar() throws Exception {
        Evento e = eventos.save(SyncFixtures.evento(proyectos.save(SyncFixtures.proyecto())));
        Asistente conAsistencia = asistentes.save(SyncFixtures.asistente("ASI-0001"));
        Asistente libre = asistentes.save(SyncFixtures.asistente("ASI-0002"));
        asistencias.save(SyncFixtures.asistencia("u-1", e, conAsistencia));

        enviar(delete("/api/asistentes/" + conAsistencia.getId()), COORD, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));
        assertTrue(asistentes.existsById(conAsistencia.getId()));

        enviar(delete("/api/asistentes/" + libre.getId()), COORD, null).andExpect(status().isOk());
        assertTrue(asistentes.findById(libre.getId()).isEmpty());
    }

    @Test
    @DisplayName("solo el COORDINADOR: sin token 401 y como MONITOR 403, en cada ruta")
    void permisos() throws Exception {
        Asistente a = asistentes.save(SyncFixtures.asistente("ASI-0001"));
        String cuerpo = "{\"tipo\":\"VECINO\",\"estadoInscripcion\":\"INSCRITO\"}";
        String[][] rutas = {{"GET", "/api/asistentes"}, {"POST", "/api/asistentes"},
                {"PUT", "/api/asistentes/" + a.getId()}, {"DELETE", "/api/asistentes/" + a.getId()}};
        for (String[] r : rutas) {
            enviar(construir(r[0], r[1]), null, cuerpo).andExpect(status().isUnauthorized());
            enviar(construir(r[0], r[1]), MONITOR, cuerpo).andExpect(status().isForbidden());
        }
        assertEquals(1, asistentes.count());
    }

    private static MockHttpServletRequestBuilder construir(String metodo, String ruta) {
        return switch (metodo) {
            case "POST" -> post(ruta);
            case "PUT" -> put(ruta);
            case "DELETE" -> delete(ruta);
            default -> get(ruta);
        };
    }
}
