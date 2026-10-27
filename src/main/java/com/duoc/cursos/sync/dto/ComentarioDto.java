package com.duoc.cursos.sync.dto;

import java.time.Instant;

/**
 * Un comentario cualitativo de una encuesta (caso §3.2 y §2.4). Sin código del asistente ni de quien registró: el
 * comentario es texto libre y no debe poder atribuirse a una persona (caso §5.1).
 */
public record ComentarioDto(Integer satisfaccion, Integer utilidad, String comentario, Instant enviadaEn) {
}
