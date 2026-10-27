package com.duoc.cursos.sync;

import com.duoc.cursos.model.Asistencia;
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
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Reporte de asistencia de un evento (caso §2.4): una fila por asistencia, con su encuesta y sin datos que identifiquen. */
@SpringBootTest
@AutoConfigureMockMvc
class AsistenciasEventoTest {

    private static final String COORD = "tok-coord";

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
        when(jwtDecoder.decode(COORD)).thenReturn(TestJwts.accessToken("sub-coord", "COORDINADOR"));
    }

    private Asistencia marcar(String uuid, String codigo, String marcadoEn) {
        Asistente a = asistentes.save(SyncFixtures.asistente(codigo));
        Asistencia asistencia = SyncFixtures.asistencia(uuid, evento, a);
        asistencia.setMarcadoEn(Instant.parse(marcadoEn));
        asistencia.setDistanciaMetros(42);
        return asistencias.save(asistencia);
    }

    @Test
    @DisplayName("devuelve una fila por asistencia, ordenada por hora de marcación, con encuesta si la hay")
    void filasOrdenadasConEncuesta() throws Exception {
        Asistencia tarde = marcar("uuid-tarde", "ASI-0002", "2026-10-28T15:30:00Z");
        marcar("uuid-temprano", "ASI-0001", "2026-10-28T15:01:00Z");
        encuestas.save(SyncFixtures.encuesta("uuid-encuesta", tarde));

        mvc.perform(get("/api/eventos/" + evento.getId() + "/asistencias").header("Authorization", "Bearer " + COORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].codigo").value("ASI-0001"))
                .andExpect(jsonPath("$.data[0].satisfaccion").doesNotExist())
                .andExpect(jsonPath("$.data[1].codigo").value("ASI-0002"))
                .andExpect(jsonPath("$.data[1].satisfaccion").value(5))
                .andExpect(jsonPath("$.data[1].utilidad").value(4))
                .andExpect(jsonPath("$.data[1].distanciaMetros").value(42))
                .andExpect(jsonPath("$.data[1].estadoInscripcion").value("INSCRITO"))
                .andExpect(jsonPath("$.data[1].tipo").value("VECINO"));
    }

    @Test
    @DisplayName("privacidad: no expone quién registró, coordenadas ni comentarios")
    void noExponeDatosQueIdentifican() throws Exception {
        Asistencia a = marcar("uuid-1", "ASI-0001", "2026-10-28T15:01:00Z");
        a.setLatitud(-33.5);
        a.setLongitud(-70.6);
        asistencias.save(a);
        encuestas.save(SyncFixtures.encuesta("uuid-encuesta", a));

        mvc.perform(get("/api/eventos/" + evento.getId() + "/asistencias").header("Authorization", "Bearer " + COORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].registradoPorSub").doesNotExist())
                .andExpect(jsonPath("$.data[0].latitud").doesNotExist())
                .andExpect(jsonPath("$.data[0].longitud").doesNotExist())
                .andExpect(jsonPath("$.data[0].comentarios").doesNotExist());
    }

    @Test
    @DisplayName("solo las asistencias del evento pedido")
    void soloDelEvento() throws Exception {
        marcar("uuid-1", "ASI-0001", "2026-10-28T15:01:00Z");
        Evento otro = eventos.save(SyncFixtures.evento(evento.getProyecto()));
        Asistente b = asistentes.save(SyncFixtures.asistente("ASI-0002"));
        asistencias.save(SyncFixtures.asistencia("uuid-otro", otro, b));

        mvc.perform(get("/api/eventos/" + evento.getId() + "/asistencias").header("Authorization", "Bearer " + COORD))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].codigo").value("ASI-0001"));
    }

    @Test
    @DisplayName("evento sin asistencias: lista vacía; evento inexistente: 404")
    void vacioYNoExiste() throws Exception {
        mvc.perform(get("/api/eventos/" + evento.getId() + "/asistencias").header("Authorization", "Bearer " + COORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        mvc.perform(get("/api/eventos/999999/asistencias").header("Authorization", "Bearer " + COORD))
                .andExpect(status().isNotFound());
    }
}
