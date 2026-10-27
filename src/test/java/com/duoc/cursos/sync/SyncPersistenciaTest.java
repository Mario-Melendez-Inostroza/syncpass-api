package com.duoc.cursos.sync;

import com.duoc.cursos.model.Asistencia;
import com.duoc.cursos.model.Asistente;
import com.duoc.cursos.model.Encuesta;
import com.duoc.cursos.model.Evento;
import com.duoc.cursos.model.Proyecto;
import com.duoc.cursos.repository.AsistenciaRepository;
import com.duoc.cursos.repository.EncuestaRepository;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Persistencia y relaciones del dominio sobre H2 (create-drop desde las entidades).
 * Las restricciones únicas son las que sostienen la idempotencia de la sincronización.
 */
@DataJpaTest
class SyncPersistenciaTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private AsistenciaRepository asistenciaRepository;

    @Autowired
    private EncuestaRepository encuestaRepository;

    private Evento evento;
    private Asistente asistente;

    @BeforeEach
    void setUp() {
        Proyecto proyecto = em.persist(SyncFixtures.proyecto());
        evento = em.persist(SyncFixtures.evento(proyecto));
        asistente = em.persist(SyncFixtures.asistente("ASI-0001"));
        em.flush();
    }

    @Test
    @DisplayName("Proyecto -> evento -> asistencia -> encuesta se guardan y se releen con sus relaciones")
    void guardaYRelacionaTodo() {
        Asistencia asistencia = em.persist(SyncFixtures.asistencia("uuid-asistencia-1", evento, asistente));
        Encuesta encuesta = em.persist(SyncFixtures.encuesta("uuid-encuesta-1", asistencia));
        em.flush();
        em.clear();

        Encuesta leida = encuestaRepository.findByUuid("uuid-encuesta-1").orElseThrow();
        assertEquals(encuesta.getId(), leida.getId());
        assertEquals("uuid-asistencia-1", leida.getAsistencia().getUuid());
        assertEquals(evento.getId(), leida.getAsistencia().getEvento().getId());
        assertEquals("ASI-0001", leida.getAsistencia().getAsistente().getCodigo());
        assertEquals(evento.getProyecto().getId(), leida.getAsistencia().getEvento().getProyecto().getId());
    }

    @Test
    @DisplayName("actualizadoEn lo fija el servidor al guardar y al modificar")
    void actualizadoEnSeFijaSolo() throws InterruptedException {
        assertNotNull(evento.getActualizadoEn());
        assertNotNull(asistente.getActualizadoEn());

        var antes = evento.getActualizadoEn();
        Thread.sleep(5);
        evento.setNombre("Taller renombrado");
        em.flush();

        assertTrue(evento.getActualizadoEn().isAfter(antes), "una modificación debe adelantar actualizadoEn");
    }

    @Test
    @DisplayName("El radio del evento tiene un valor por defecto")
    void radioPorDefecto() {
        Evento sinRadio = SyncFixtures.evento(evento.getProyecto());
        sinRadio.setRadioMetros(Evento.RADIO_POR_DEFECTO_METROS);
        assertEquals(200, em.persistAndFlush(sinRadio).getRadioMetros());
        assertEquals(200, new Evento().getRadioMetros());
    }

    @Test
    @DisplayName("Dos asistencias con el mismo uuid violan la restricción única (idempotencia)")
    void uuidDeAsistenciaEsUnico() {
        em.persistAndFlush(SyncFixtures.asistencia("uuid-repetido", evento, asistente));
        Asistente otro = em.persistAndFlush(SyncFixtures.asistente("ASI-0002"));

        assertThrows(DataIntegrityViolationException.class, () ->
                asistenciaRepository.saveAndFlush(SyncFixtures.asistencia("uuid-repetido", evento, otro)));
    }

    @Test
    @DisplayName("Un asistente solo puede marcar una vez por evento, aunque cambie el uuid")
    void unaMarcacionPorEventoYAsistente() {
        em.persistAndFlush(SyncFixtures.asistencia("uuid-uno", evento, asistente));

        assertThrows(DataIntegrityViolationException.class, () ->
                asistenciaRepository.saveAndFlush(SyncFixtures.asistencia("uuid-dos", evento, asistente)));
    }

    @Test
    @DisplayName("El mismo asistente SÍ puede marcar en otro evento")
    void mismoAsistenteEnOtroEvento() {
        em.persistAndFlush(SyncFixtures.asistencia("uuid-uno", evento, asistente));
        Evento otroEvento = em.persistAndFlush(SyncFixtures.evento(evento.getProyecto()));

        Asistencia segunda = asistenciaRepository.saveAndFlush(
                SyncFixtures.asistencia("uuid-dos", otroEvento, asistente));

        assertNotNull(segunda.getId());
    }

    @Test
    @DisplayName("Una asistencia solo admite una encuesta")
    void unaEncuestaPorAsistencia() {
        Asistencia asistencia = em.persistAndFlush(SyncFixtures.asistencia("uuid-a", evento, asistente));
        em.persistAndFlush(SyncFixtures.encuesta("uuid-e1", asistencia));

        assertThrows(DataIntegrityViolationException.class, () ->
                encuestaRepository.saveAndFlush(SyncFixtures.encuesta("uuid-e2", asistencia)));
    }

    @Test
    @DisplayName("El uuid de una encuesta es único")
    void uuidDeEncuestaEsUnico() {
        Asistencia a1 = em.persistAndFlush(SyncFixtures.asistencia("uuid-a1", evento, asistente));
        Asistente otro = em.persistAndFlush(SyncFixtures.asistente("ASI-0002"));
        Asistencia a2 = em.persistAndFlush(SyncFixtures.asistencia("uuid-a2", evento, otro));
        em.persistAndFlush(SyncFixtures.encuesta("uuid-e", a1));

        assertThrows(DataIntegrityViolationException.class, () ->
                encuestaRepository.saveAndFlush(SyncFixtures.encuesta("uuid-e", a2)));
    }

    @Test
    @DisplayName("La escala de la encuesta es 1-5: fuera de rango no se guarda")
    void escalaDeLaEncuesta() {
        Asistencia asistencia = em.persistAndFlush(SyncFixtures.asistencia("uuid-a", evento, asistente));
        Encuesta invalida = SyncFixtures.encuesta("uuid-e", asistencia);
        invalida.setSatisfaccion(6);

        assertThrows(ConstraintViolationException.class, () -> em.persistAndFlush(invalida));
    }

    @Test
    @DisplayName("Asistente NO tiene columnas de datos personales (caso §5.1)")
    void asistenteNoGuardaDatosPersonales() {
        Set<String> campos = Arrays.stream(Asistente.class.getDeclaredFields())
                .filter(f -> !f.isSynthetic())
                .map(Field::getName)
                .collect(Collectors.toSet());

        // Si alguien añade nombre, email, teléfono, documento... esta prueba obliga a justificarlo.
        // uuid: UUID aleatorio que genera el teléfono como clave de idempotencia del alta en terreno (V3). No identifica
        // a ninguna persona: no se deriva de ningún dato suyo y solo sirve para no duplicar el alta al reenviar.
        assertEquals(Set.of("id", "codigo", "uuid", "tipo", "estadoInscripcion", "actualizadoEn"), campos);
    }
}
