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

import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Quórum mínimo de un evento (caso §3.1: el coordinador "supervisa el cumplimiento del quórum mínimo"). */
@SpringBootTest
@AutoConfigureMockMvc
class QuorumMinimoTest {

    private static final String COORD = "tok-coord";

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
    }

    private Evento eventoConMinimo(Integer minimo) {
        Evento e = SyncFixtures.evento(proyecto);
        e.setQuorumMinimo(minimo);
        return eventos.save(e);
    }

    private void marcar(Evento evento, int cuantos) {
        for (int i = 0; i < cuantos; i++) {
            Asistente a = asistentes.save(SyncFixtures.asistente("ASI-" + evento.getId() + "-" + i));
            asistencias.save(SyncFixtures.asistencia("u-" + evento.getId() + "-" + i, evento, a));
        }
    }

    private String json(String extra) {
        return "{\"proyectoId\":" + proyecto.getId() + ",\"nombre\":\"Taller\",\"fechaHoraProgramada\":\"2026-11-20T15:00:00Z\","
                + "\"cuposMaximos\":30" + extra + "}";
    }

    @Test
    @DisplayName("sin mínimo definido el resumen no dice ni cumplido ni incumplido")
    void sinMinimo() throws Exception {
        Evento e = eventoConMinimo(null);
        marcar(e, 3);

        mvc.perform(get("/api/eventos/" + e.getId() + "/resumen").header("Authorization", "Bearer " + COORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quorumMinimo").doesNotExist())
                .andExpect(jsonPath("$.data.quorumCumplido").doesNotExist());
    }

    @Test
    @DisplayName("con menos marcados que el mínimo el quórum NO está cumplido")
    void incumplido() throws Exception {
        Evento e = eventoConMinimo(5);
        marcar(e, 4);

        mvc.perform(get("/api/eventos/" + e.getId() + "/resumen").header("Authorization", "Bearer " + COORD))
                .andExpect(jsonPath("$.data.quorumMinimo").value(5))
                .andExpect(jsonPath("$.data.quorumCumplido").value(false))
                .andExpect(jsonPath("$.data.totalMarcados").value(4));
    }

    @Test
    @DisplayName("al llegar exactamente al mínimo el quórum se cumple (el borde cuenta)")
    void cumplidoEnElBorde() throws Exception {
        Evento e = eventoConMinimo(5);
        marcar(e, 5);

        mvc.perform(get("/api/eventos/" + e.getId() + "/resumen").header("Authorization", "Bearer " + COORD))
                .andExpect(jsonPath("$.data.quorumCumplido").value(true));
    }

    @Test
    @DisplayName("crear un evento con quórum mínimo: se guarda y aparece en el catálogo")
    void crearConMinimo() throws Exception {
        mvc.perform(post("/api/eventos").header("Authorization", "Bearer " + COORD)
                        .contentType(MediaType.APPLICATION_JSON).content(json(",\"quorumMinimo\":10")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.quorumMinimo").value(10));

        mvc.perform(get("/api/sync/catalogo").header("Authorization", "Bearer " + COORD))
                .andExpect(jsonPath("$.data.eventos[*].quorumMinimo", hasItem(10)));
    }

    @Test
    @DisplayName("un mínimo mayor que los cupos es 400: nunca se podría cumplir")
    void minimoMayorQueLosCupos() throws Exception {
        mvc.perform(post("/api/eventos").header("Authorization", "Bearer " + COORD)
                        .contentType(MediaType.APPLICATION_JSON).content(json(",\"quorumMinimo\":31")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles.quorumCoherente").exists());

        assertEquals(0, eventos.count());
    }

    @Test
    @DisplayName("un mínimo de cero o negativo es 400")
    void minimoInvalido() throws Exception {
        mvc.perform(post("/api/eventos").header("Authorization", "Bearer " + COORD)
                        .contentType(MediaType.APPLICATION_JSON).content(json(",\"quorumMinimo\":0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles.quorumMinimo").exists());
    }

    @Test
    @DisplayName("editar el evento puede definir, cambiar o quitar el mínimo")
    void editarMinimo() throws Exception {
        Evento e = eventoConMinimo(null);

        mvc.perform(put("/api/eventos/" + e.getId()).header("Authorization", "Bearer " + COORD)
                        .contentType(MediaType.APPLICATION_JSON).content(json(",\"quorumMinimo\":8")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quorumMinimo").value(8));
        assertEquals(8, eventos.findById(e.getId()).orElseThrow().getQuorumMinimo());

        mvc.perform(put("/api/eventos/" + e.getId()).header("Authorization", "Bearer " + COORD)
                        .contentType(MediaType.APPLICATION_JSON).content(json("")))
                .andExpect(status().isOk());
        assertEquals(null, eventos.findById(e.getId()).orElseThrow().getQuorumMinimo());
    }
}
