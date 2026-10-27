package com.duoc.cursos.sync.dto;

import com.duoc.cursos.model.enums.EstadoInscripcion;
import com.duoc.cursos.model.enums.EstadoValidacion;
import com.duoc.cursos.model.enums.TipoParticipante;

import java.time.Instant;

/**
 * Una fila del reporte de asistencia de un evento (caso §2.4: reportes automáticos).
 *
 * Deliberadamente sin quién registró, coordenadas ni comentarios libres: el reporte no necesita datos que
 * identifiquen a nadie (caso §5.1). Satisfacción y utilidad son null si el asistente no respondió la encuesta.
 */
public record AsistenciaFilaDto(
        String codigo,
        TipoParticipante tipo,
        EstadoInscripcion estadoInscripcion,
        Instant marcadoEn,
        EstadoValidacion estadoValidacion,
        Integer distanciaMetros,
        Integer satisfaccion,
        Integer utilidad) {
}
