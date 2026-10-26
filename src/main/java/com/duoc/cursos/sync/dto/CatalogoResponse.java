package com.duoc.cursos.sync.dto;

import java.time.Instant;
import java.util.List;

/**
 * @param servidorEn hora del servidor al armar la respuesta. El cliente la guarda y la manda como
 *                   ?desde= la próxima vez: se usa el reloj del servidor porque el del teléfono puede ir
 *                   desviado y haría perder filas.
 */
public record CatalogoResponse(
        Instant servidorEn,
        List<ProyectoDto> proyectos,
        List<EventoDto> eventos,
        List<AsistenteDto> asistentes) {
}
