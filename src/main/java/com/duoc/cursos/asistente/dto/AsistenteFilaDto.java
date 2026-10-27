package com.duoc.cursos.asistente.dto;

import com.duoc.cursos.model.enums.EstadoInscripcion;
import com.duoc.cursos.model.enums.TipoParticipante;

/**
 * Una fila de la lista de asistentes del coordinador.
 *
 * @param totalAsistencias en cuántos eventos fue marcado.
 * @param creadoEnTerreno  true si lo dio de alta un monitor desde la app (llegó sin inscripción previa).
 */
public record AsistenteFilaDto(
        Long id,
        String codigo,
        TipoParticipante tipo,
        EstadoInscripcion estadoInscripcion,
        long totalAsistencias,
        boolean creadoEnTerreno) {
}
