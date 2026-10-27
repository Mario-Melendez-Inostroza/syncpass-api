package com.duoc.cursos.sync.dto;

import java.time.Instant;

/**
 * Quórum y satisfacción de un evento. Los promedios son null mientras no haya encuestas.
 *
 * @param quorumMinimo   el mínimo que definió el coordinador, o null si el evento no define uno.
 * @param quorumCumplido true si {@code totalMarcados >= quorumMinimo}; null si no hay mínimo (no aplica).
 */
public record ResumenEventoResponse(
        Long eventoId,
        String nombre,
        Integer cuposMaximos,
        Integer quorumMinimo,
        Boolean quorumCumplido,
        long totalMarcados,
        long validas,
        long fueraDeRadio,
        long sinUbicacion,
        long encuestasRecibidas,
        Double satisfaccionPromedio,
        Double utilidadPromedio,
        Instant primeraMarcacion,
        Instant ultimaMarcacion) {

    /** null cuando no hay mínimo definido: no se puede decir "cumplido" ni "incumplido". */
    public static Boolean cumple(Integer quorumMinimo, long totalMarcados) {
        return quorumMinimo == null ? null : totalMarcados >= quorumMinimo;
    }
}
