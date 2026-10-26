package com.duoc.cursos.model.enums;

/**
 * Resultado de contrastar la ubicación de la marcación con el radio del evento.
 * El GPS es referencial (caso §3.3): clasifica la marcación, nunca la rechaza.
 */
public enum EstadoValidacion {
    VALIDA,
    FUERA_DE_RADIO,
    SIN_UBICACION
}
