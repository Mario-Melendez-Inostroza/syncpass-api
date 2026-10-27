package com.duoc.cursos.sync;

import com.duoc.cursos.model.Asistencia;
import com.duoc.cursos.model.Asistente;
import com.duoc.cursos.model.Encuesta;
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
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Informe de impacto consolidado (caso §2.2 punto 4 y §2.4) y comentarios cualitativos (§3.2).
 *
 * Datos: proyecto "Alfabetización digital" con E1 (mínimo 3, marcan A1 A2 A3) y E2 (sin mínimo, marcan A1 A4), y un segundo
 * proyecto "Zona verde" con E3 (marca A5). Encuestas: A1 y A2 en E1; A4 en E2.
 */
@SpringBootTest
@AutoConfigureMockMvc
class InformeTest {

    private static final String COORD = "tok-coord";
    private static final String MONITOR = "tok-monitor";

    @Autowired private MockMvc mvc;
    @Autowired private ProyectoRepository proyectos;
    @Autowired private EventoRepository eventos;
    @Autowired private AsistenteRepository asistentes;
    @Autowired private AsistenciaRepository asistencias;
    @Autowired private EncuestaRepository encuestas;

    @MockitoBean private JwtDecoder jwtDecoder;

    private Proyecto p1;
    private Proyecto p2;
    private Evento e1;
    private Evento e2;
    private Evento e3;

    @BeforeEach
    void setUp() {
        encuestas.deleteAll();
        asistencias.deleteAll();
        asistentes.deleteAll();
        eventos.deleteAll();
        proyectos.deleteAll();

        p1 = proyectos.save(SyncFixtures.proyecto());
        Proyecto otro = SyncFixtures.proyecto();
        otro.setTitulo("Zona verde");
        p2 = proyectos.save(otro);

        e1 = evento(p1, "Taller 1", "2026-10-28T15:00:00Z", 3);
        e2 = evento(p1, "Taller 2", "2026-11-05T15:00:00Z", null);
        e3 = evento(p2, "Jornada", "2026-11-10T15:00:00Z", null);

        Asistente a1 = asistentes.save(SyncFixtures.asistente("ASI-0001"));
        Asistente a2 = asistentes.save(SyncFixtures.asistente("ASI-0002"));
        Asistente a3 = asistentes.save(SyncFixtures.asistente("ASI-0003"));
        Asistente a4 = asistentes.save(SyncFixtures.asistente("ASI-0004"));
        Asistente a5 = asistentes.save(SyncFixtures.asistente("ASI-0005"));

        Asistencia m11 = asistencias.save(SyncFixtures.asistencia("m-e1-a1", e1, a1));
        Asistencia m12 = asistencias.save(SyncFixtures.asistencia("m-e1-a2", e1, a2));
        asistencias.save(SyncFixtures.asistencia("m-e1-a3", e1, a3));
        asistencias.save(SyncFixtures.asistencia("m-e2-a1", e2, a1));
        Asistencia m24 = asistencias.save(SyncFixtures.asistencia("m-e2-a4", e2, a4));
        asistencias.save(SyncFixtures.asistencia("m-e3-a5", e3, a5));

        encuesta("enc-1", m11, 5, 4, "Muy útil", "2026-10-28T16:00:00Z");
        encuesta("enc-2", m12, 3, 3, "   ", "2026-10-28T16:05:00Z"); // comentario en blanco: no cuenta como comentario
        encuesta("enc-3", m24, 5, 5, "Excelente", "2026-11-05T16:00:00Z");

        when(jwtDecoder.decode(COORD)).thenReturn(TestJwts.accessToken("sub-coord", "COORDINADOR"));
        when(jwtDecoder.decode(MONITOR)).thenReturn(TestJwts.accessToken("sub-monitor", "MONITOR"));
    }

    private Evento evento(Proyecto p, String nombre, String fecha, Integer minimo) {
        Evento e = SyncFixtures.evento(p);
        e.setNombre(nombre);
        e.setFechaHoraProgramada(Instant.parse(fecha));
        e.setQuorumMinimo(minimo);
        return eventos.save(e);
    }

    private void encuesta(String uuid, Asistencia a, int satisfaccion, int utilidad, String comentario, String enviada) {
        Encuesta e = SyncFixtures.encuesta(uuid, a);
        e.setSatisfaccion(satisfaccion);
        e.setUtilidad(utilidad);
        e.setComentarios(comentario);
        e.setEnviadaEn(Instant.parse(enviada));
        encuestas.save(e);
    }

    private org.springframework.test.web.servlet.ResultActions pedir(String ruta) throws Exception {
        return mvc.perform(get(ruta).header("Authorization", "Bearer " + COORD));
    }

    @Test
    @DisplayName("informe de un proyecto: cifras por evento, quórum cumplido y tasa de respuesta")
    void informeDeUnProyecto() throws Exception {
        pedir("/api/informes?proyectoId=" + p1.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.proyectos.length()").value(1))
                .andExpect(jsonPath("$.data.proyectos[0].proyecto.titulo").value("Alfabetización digital"))
                .andExpect(jsonPath("$.data.proyectos[0].eventos.length()").value(2))
                // E1: ordenado por fecha, mínimo 3 y marcaron 3 -> cumplido; 2 de 3 respondieron.
                .andExpect(jsonPath("$.data.proyectos[0].eventos[0].nombre").value("Taller 1"))
                .andExpect(jsonPath("$.data.proyectos[0].eventos[0].totalMarcados").value(3))
                .andExpect(jsonPath("$.data.proyectos[0].eventos[0].quorumMinimo").value(3))
                .andExpect(jsonPath("$.data.proyectos[0].eventos[0].quorumCumplido").value(true))
                .andExpect(jsonPath("$.data.proyectos[0].eventos[0].encuestasRecibidas").value(2))
                .andExpect(jsonPath("$.data.proyectos[0].eventos[0].tasaRespuesta").value(0.667))
                .andExpect(jsonPath("$.data.proyectos[0].eventos[0].satisfaccionPromedio").value(4.0))
                // E2: sin mínimo no hay "cumplido" ni "incumplido".
                .andExpect(jsonPath("$.data.proyectos[0].eventos[1].nombre").value("Taller 2"))
                .andExpect(jsonPath("$.data.proyectos[0].eventos[1].quorumMinimo").doesNotExist())
                .andExpect(jsonPath("$.data.proyectos[0].eventos[1].quorumCumplido").doesNotExist());
    }

    @Test
    @DisplayName("totales: cuenta a cada persona una sola vez y pondera los promedios por encuesta")
    void totalesDelProyecto() throws Exception {
        pedir("/api/informes?proyectoId=" + p1.getId())
                .andExpect(jsonPath("$.data.totales.eventos").value(2))
                .andExpect(jsonPath("$.data.totales.eventosConQuorumMinimo").value(1))
                .andExpect(jsonPath("$.data.totales.eventosQuorumCumplido").value(1))
                .andExpect(jsonPath("$.data.totales.totalMarcados").value(5))
                // A1 asistió a dos eventos: 5 marcaciones, 4 personas distintas.
                .andExpect(jsonPath("$.data.totales.asistentesUnicos").value(4))
                .andExpect(jsonPath("$.data.totales.encuestasRecibidas").value(3))
                .andExpect(jsonPath("$.data.totales.tasaRespuesta").value(0.6))
                // Satisfacción: E1 promedia 4,0 con 2 encuestas y E2 promedia 5,0 con 1 -> (4*2+5)/3 = 4,33
                // (un promedio de promedios daría 4,5).
                .andExpect(jsonPath("$.data.totales.satisfaccionPromedio").value(4.33))
                .andExpect(jsonPath("$.data.totales.utilidadPromedio").value(4.0));
    }

    @Test
    @DisplayName("sin proyectoId el informe es general: todos los proyectos ordenados por título")
    void informeGeneral() throws Exception {
        pedir("/api/informes")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.proyectos.length()").value(2))
                .andExpect(jsonPath("$.data.proyectos[0].proyecto.titulo").value("Alfabetización digital"))
                .andExpect(jsonPath("$.data.proyectos[1].proyecto.titulo").value("Zona verde"))
                .andExpect(jsonPath("$.data.totales.eventos").value(3))
                .andExpect(jsonPath("$.data.totales.totalMarcados").value(6))
                .andExpect(jsonPath("$.data.totales.asistentesUnicos").value(5))
                .andExpect(jsonPath("$.data.totales.tasaRespuesta").value(0.5));
    }

    @Test
    @DisplayName("el informe trae los comentarios sin vacíos y sin nada que identifique a quien los escribió")
    void comentariosEnElInforme() throws Exception {
        pedir("/api/informes?proyectoId=" + p1.getId())
                .andExpect(jsonPath("$.data.proyectos[0].eventos[0].comentarios.length()").value(1))
                .andExpect(jsonPath("$.data.proyectos[0].eventos[0].comentarios[0].comentario").value("Muy útil"))
                .andExpect(jsonPath("$.data.proyectos[0].eventos[0].comentarios[0].satisfaccion").value(5))
                .andExpect(jsonPath("$.data.proyectos[0].eventos[0].comentarios[0].codigo").doesNotExist())
                .andExpect(jsonPath("$.data.proyectos[0].eventos[0].comentarios[0].asistente").doesNotExist())
                .andExpect(jsonPath("$.data.proyectos[0].eventos[0].comentarios[0].registradoPorSub").doesNotExist())
                .andExpect(jsonPath("$.data.proyectos[0].eventos[1].comentarios[0].comentario").value("Excelente"));
    }

    @Test
    @DisplayName("un proyecto sin eventos da un informe vacío con tasas nulas, no un error")
    void proyectoSinEventos() throws Exception {
        Proyecto vacio = SyncFixtures.proyecto();
        vacio.setTitulo("Sin eventos");
        vacio = proyectos.save(vacio);

        pedir("/api/informes?proyectoId=" + vacio.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.proyectos[0].eventos.length()").value(0))
                .andExpect(jsonPath("$.data.totales.eventos").value(0))
                .andExpect(jsonPath("$.data.totales.totalMarcados").value(0))
                .andExpect(jsonPath("$.data.totales.tasaRespuesta").doesNotExist())
                .andExpect(jsonPath("$.data.totales.satisfaccionPromedio").doesNotExist());
    }

    @Test
    @DisplayName("un proyecto que no existe es 404")
    void proyectoInexistente() throws Exception {
        pedir("/api/informes?proyectoId=999999").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("comentarios de un evento: los más recientes primero, sin vacíos; evento inexistente es 404")
    void comentariosDeUnEvento() throws Exception {
        // Se agrega un segundo comentario más reciente en E1.
        Asistencia m13 = asistencias.findAll().stream().filter(a -> a.getUuid().equals("m-e1-a3")).findFirst().orElseThrow();
        encuesta("enc-4", m13, 4, 4, "Llegó tarde la monitora", "2026-10-28T17:00:00Z");

        pedir("/api/eventos/" + e1.getId() + "/comentarios")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].comentario").value("Llegó tarde la monitora"))
                .andExpect(jsonPath("$.data[1].comentario").value("Muy útil"))
                .andExpect(jsonPath("$.data[0].codigo").doesNotExist());

        pedir("/api/eventos/999999/comentarios").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("el informe y los comentarios son solo del COORDINADOR: sin token 401 y como MONITOR 403")
    void permisos() throws Exception {
        for (String ruta : new String[]{"/api/informes", "/api/informes?proyectoId=" + p1.getId(),
                "/api/eventos/" + e1.getId() + "/comentarios"}) {
            mvc.perform(get(ruta)).andExpect(status().isUnauthorized());
            mvc.perform(get(ruta).header("Authorization", "Bearer " + MONITOR)).andExpect(status().isForbidden());
        }
    }
}
