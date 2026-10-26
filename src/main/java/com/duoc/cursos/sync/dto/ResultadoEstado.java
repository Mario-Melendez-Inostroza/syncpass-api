package com.duoc.cursos.sync.dto;

/**
 * Qué pasó con un registro del lote. ACEPTADO y DUPLICADO significan lo mismo para el cliente: el
 * registro ya existe en el servidor y puede marcarse como sincronizado.
 */
public enum ResultadoEstado {
    ACEPTADO,
    DUPLICADO,
    RECHAZADO
}
