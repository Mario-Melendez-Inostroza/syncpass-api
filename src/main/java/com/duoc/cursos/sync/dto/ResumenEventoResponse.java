package com.duoc.cursos.sync.dto;

import java.time.Instant;

/** Quórum y satisfacción de un evento. Los promedios son null mientras no haya encuestas. */
public record ResumenEventoResponse(
        Long eventoId,
        String nombre,
        Integer cuposMaximos,
        long totalMarcados,
        long validas,
        long fueraDeRadio,
        long sinUbicacion,
        long encuestasRecibidas,
        Double satisfaccionPromedio,
        Double utilidadPromedio,
        Instant primeraMarcacion,
        Instant ultimaMarcacion) {
}
