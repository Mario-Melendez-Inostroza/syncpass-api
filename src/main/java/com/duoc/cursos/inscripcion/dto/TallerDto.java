package com.duoc.cursos.inscripcion.dto;

import java.time.Instant;

/** Un taller visto por el asistente: cuántos cupos quedan y si ya está inscrito. */
public record TallerDto(Long id, String nombre, String proyecto, Instant fechaHoraProgramada, String recinto,
                        Integer cuposMaximos, long inscritos, Integer cuposDisponibles, boolean inscrito) {
}
