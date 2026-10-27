package com.duoc.cursos.sync;

import com.duoc.cursos.model.Asistente;
import com.duoc.cursos.model.Evento;
import com.duoc.cursos.model.Proyecto;
import com.duoc.cursos.model.enums.EstadoInscripcion;
import com.duoc.cursos.model.enums.EstadoProyecto;
import com.duoc.cursos.model.enums.TipoParticipante;
import com.duoc.cursos.repository.AsistenciaRepository;
import com.duoc.cursos.repository.AsistenteRepository;
import com.duoc.cursos.repository.EncuestaRepository;
import com.duoc.cursos.repository.EventoRepository;
import com.duoc.cursos.repository.InscripcionRepository;
import com.duoc.cursos.repository.ProyectoRepository;
import com.duoc.cursos.service.AuthService;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** La cuenta del Asistente (caso §3.1): registro, talleres, inscripción con cupos y QR; sin datos personales (§5.1). */
@SpringBootTest
@AutoConfigureMockMvc
class CuentaAsistenteTest {

    private static final String ASISTENTE = "tok-asistente";
    private static final String OTRO = "tok-otro";
    private static final String MONITOR = "tok-monitor";

    @Autowired private MockMvc mvc;
    @Autowired private ProyectoRepository proyectos;
    @Autowired private EventoRepository eventos;
    @Autowired private AsistenteRepository asistentes;
    @Autowired private AsistenciaRepository asistencias;
    @Autowired private EncuestaRepository encuestas;
    @Autowired private InscripcionRepository inscripciones;

    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private AuthService authService;

    private Evento taller;

    @BeforeEach
    void setUp() {
        inscripciones.deleteAll();
        encuestas.deleteAll();
        asistencias.deleteAll();
        asistentes.deleteAll();
        eventos.deleteAll();
        proyectos.deleteAll();
        when(jwtDecoder.decode(ASISTENTE)).thenReturn(TestJwts.accessToken("sub-asistente", "ASISTENTE"));
        when(jwtDecoder.decode(OTRO)).thenReturn(TestJwts.accessToken("sub-otro", "ASISTENTE"));
        when(jwtDecoder.decode(MONITOR)).thenReturn(TestJwts.accessToken("sub-monitor", "MONITOR"));

        Proyecto p = new Proyecto();
        p.setTitulo("Alfabetización digital");
        p.setAreaTematica("tecnologia");
        p.setUnidadVecinal("UV-12");
        p.setFechaInicio(LocalDate.now());
        p.setFechaTermino(LocalDate.now().plusDays(30));
        p.setEstado(EstadoProyecto.EN_CURSO);
        p = proyectos.save(p);
        taller = nuevoTaller(p, "Taller de correo", Instant.now().plus(2, ChronoUnit.DAYS), 1);
    }

    private Evento nuevoTaller(Proyecto p, String nombre, Instant cuando, Integer cupos) {
        Evento e = new Evento();
        e.setProyecto(p);
        e.setNombre(nombre);
        e.setFechaHoraProgramada(cuando);
        e.setCuposMaximos(cupos);
        return eventos.save(e);
    }

    private Asistente cuenta(String sub, String codigo) {
        Asistente a = new Asistente();
        a.setCodigo(codigo);
        a.setTipo(TipoParticipante.VECINO);
        a.setEstadoInscripcion(EstadoInscripcion.INSCRITO);
        a.setUsuarioSub(sub);
        return asistentes.save(a);
    }

    private ResultActions enviar(MockHttpServletRequestBuilder peticion, String token, String json) throws Exception {
        if (token != null) peticion.header("Authorization", "Bearer " + token);
        if (json != null) peticion.contentType(MediaType.APPLICATION_JSON).content(json);
        return mvc.perform(peticion);
    }

    @Test
    @DisplayName("registro: crea la cuenta en el grupo ASISTENTE y un asistente sintético vinculado al sub, sin correo en la base")
    void registro() throws Exception {
        when(authService.crearCuentaEnGrupo(eq("vecina@example.com"), any(), any(), eq("ASISTENTE"))).thenReturn("sub-nuevo");

        enviar(post("/api/auth/registro-asistente"), null,
                "{\"email\":\"vecina@example.com\",\"password\":\"Clave-segura-1\",\"tipo\":\"VECINO\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.codigo").value(org.hamcrest.Matchers.matchesPattern("REG-[0-9A-F]{8}")))
                .andExpect(jsonPath("$.data.tipo").value("VECINO"))
                .andExpect(jsonPath("$.data.qr").value(org.hamcrest.Matchers.startsWith("ASISTENTE_")));

        Asistente guardado = asistentes.findByUsuarioSub("sub-nuevo").orElseThrow();
        assertEquals(EstadoInscripcion.INSCRITO, guardado.getEstadoInscripcion());
        verify(authService).crearCuentaEnGrupo(eq("vecina@example.com"), any(), any(), eq("ASISTENTE"));
    }

    @Test
    @DisplayName("registro: tipo obligatorio y correo válido")
    void registroInvalido() throws Exception {
        enviar(post("/api/auth/registro-asistente"), null, "{\"email\":\"x@example.com\",\"password\":\"Clave-segura-1\"}")
                .andExpect(status().isBadRequest());
        enviar(post("/api/auth/registro-asistente"), null, "{\"email\":\"no-es-correo\",\"password\":\"Clave-segura-1\",\"tipo\":\"VECINO\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("mi perfil: devuelve el código y el texto del QR que lee la app del monitor")
    void perfil() throws Exception {
        Asistente a = cuenta("sub-asistente", "REG-AAAA1111");

        enviar(get("/api/mi/perfil"), ASISTENTE, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.codigo").value("REG-AAAA1111"))
                .andExpect(jsonPath("$.data.qr").value("ASISTENTE_" + a.getId()));
    }

    @Test
    @DisplayName("una cuenta sin perfil de asistente recibe 404, no un 500")
    void sinPerfil() throws Exception {
        enviar(get("/api/mi/perfil"), ASISTENTE, null).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("talleres: solo los próximos, con cupos disponibles y si ya estoy inscrito")
    void talleres() throws Exception {
        cuenta("sub-asistente", "REG-AAAA1111");
        nuevoTaller(taller.getProyecto(), "Taller viejo", Instant.now().minus(3, ChronoUnit.DAYS), 10);

        enviar(get("/api/mi/talleres"), ASISTENTE, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].nombre").value("Taller de correo"))
                .andExpect(jsonPath("$.data[0].proyecto").value("Alfabetización digital"))
                .andExpect(jsonPath("$.data[0].cuposDisponibles").value(1))
                .andExpect(jsonPath("$.data[0].inscrito").value(false));

        enviar(post("/api/mi/talleres/" + taller.getId() + "/inscripcion"), ASISTENTE, null).andExpect(status().isCreated());

        enviar(get("/api/mi/talleres"), ASISTENTE, null)
                .andExpect(jsonPath("$.data[0].cuposDisponibles").value(0))
                .andExpect(jsonPath("$.data[0].inscrito").value(true));
    }

    @Test
    @DisplayName("inscripción: no se repite (409), respeta los cupos (409) y se puede cancelar")
    void inscripcion() throws Exception {
        cuenta("sub-asistente", "REG-AAAA1111");
        cuenta("sub-otro", "REG-BBBB2222");
        String url = "/api/mi/talleres/" + taller.getId() + "/inscripcion";

        enviar(post(url), ASISTENTE, null).andExpect(status().isCreated());
        enviar(post(url), ASISTENTE, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("Ya estás inscrito en este taller."));
        // El taller tenía un solo cupo.
        enviar(post(url), OTRO, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("El taller no tiene cupos disponibles."));

        enviar(delete(url), ASISTENTE, null).andExpect(status().isOk());
        enviar(delete(url), ASISTENTE, null).andExpect(status().isOk()); // idempotente
        enviar(post(url), OTRO, null).andExpect(status().isCreated());
        assertEquals(1, inscripciones.count());
    }

    @Test
    @DisplayName("inscripción a un taller inexistente o ya ocurrido")
    void tallerInvalido() throws Exception {
        cuenta("sub-asistente", "REG-AAAA1111");
        Evento viejo = nuevoTaller(taller.getProyecto(), "Taller viejo", Instant.now().minus(3, ChronoUnit.DAYS), null);

        enviar(post("/api/mi/talleres/999999/inscripcion"), ASISTENTE, null).andExpect(status().isNotFound());
        enviar(post("/api/mi/talleres/" + viejo.getId() + "/inscripcion"), ASISTENTE, null).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("seguridad: /api/mi es solo del grupo ASISTENTE y el asistente no puede usar la sincronización del equipo")
    void seguridad() throws Exception {
        enviar(get("/api/mi/talleres"), null, null).andExpect(status().isUnauthorized());
        enviar(get("/api/mi/talleres"), MONITOR, null).andExpect(status().isForbidden());
        enviar(get("/api/sync/catalogo"), ASISTENTE, null).andExpect(status().isForbidden());
        enviar(get("/api/asistentes"), ASISTENTE, null).andExpect(status().isForbidden());
    }
}
