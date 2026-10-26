package com.duoc.cursos.sync;

import com.duoc.cursos.model.Asistencia;
import com.duoc.cursos.model.Asistente;
import com.duoc.cursos.model.Encuesta;
import com.duoc.cursos.model.Evento;
import com.duoc.cursos.model.Proyecto;
import com.duoc.cursos.model.enums.EstadoInscripcion;
import com.duoc.cursos.model.enums.EstadoProyecto;
import com.duoc.cursos.model.enums.EstadoValidacion;
import com.duoc.cursos.model.enums.TipoParticipante;

import java.time.Instant;
import java.time.LocalDate;

/** Entidades ficticias (sin guardar). Todo dato es inventado: el caso prohíbe datos personales (§5.1). */
final class SyncFixtures {

    static final double LAT_RECINTO = -33.5121;
    static final double LON_RECINTO = -70.6003;

    private SyncFixtures() {
    }

    static Proyecto proyecto() {
        Proyecto p = new Proyecto();
        p.setTitulo("Alfabetización digital");
        p.setDescripcion("Proyecto ficticio de prueba");
        p.setAreaTematica("tecnologia");
        p.setUnidadVecinal("UV-12");
        p.setFechaInicio(LocalDate.of(2026, 8, 1));
        p.setFechaTermino(LocalDate.of(2026, 12, 15));
        p.setEstado(EstadoProyecto.EN_CURSO);
        return p;
    }

    static Evento evento(Proyecto proyecto) {
        Evento e = new Evento();
        e.setProyecto(proyecto);
        e.setNombre("Taller de correo electrónico");
        e.setFechaHoraProgramada(Instant.parse("2026-10-28T15:00:00Z"));
        e.setCuposMaximos(30);
        e.setRecinto("Sede UV-12");
        e.setLatitud(LAT_RECINTO);
        e.setLongitud(LON_RECINTO);
        e.setRadioMetros(200);
        return e;
    }

    static Asistente asistente(String codigo) {
        Asistente a = new Asistente();
        a.setCodigo(codigo);
        a.setTipo(TipoParticipante.VECINO);
        a.setEstadoInscripcion(EstadoInscripcion.INSCRITO);
        return a;
    }

    static Asistencia asistencia(String uuid, Evento evento, Asistente asistente) {
        Asistencia a = new Asistencia();
        a.setUuid(uuid);
        a.setEvento(evento);
        a.setAsistente(asistente);
        a.setMarcadoEn(Instant.parse("2026-10-28T15:04:11Z"));
        a.setEstadoValidacion(EstadoValidacion.SIN_UBICACION);
        a.setRegistradoPorSub("sub-de-prueba");
        a.setRecibidoEn(Instant.now());
        return a;
    }

    static Encuesta encuesta(String uuid, Asistencia asistencia) {
        Encuesta e = new Encuesta();
        e.setUuid(uuid);
        e.setAsistencia(asistencia);
        e.setSatisfaccion(5);
        e.setUtilidad(4);
        e.setComentarios("Muy útil");
        e.setEnviadaEn(Instant.parse("2026-10-28T16:10:00Z"));
        e.setRecibidaEn(Instant.now());
        return e;
    }
}
