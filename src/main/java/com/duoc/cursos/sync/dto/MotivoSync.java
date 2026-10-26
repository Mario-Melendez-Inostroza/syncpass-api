package com.duoc.cursos.sync.dto;

/** Motivos que acompañan a DUPLICADO y RECHAZADO. Son parte del contrato con el móvil. */
public final class MotivoSync {

    private MotivoSync() {
    }

    // DUPLICADO
    public static final String UUID_YA_REGISTRADO = "UUID_YA_REGISTRADO";
    public static final String ASISTENTE_YA_MARCADO = "ASISTENTE_YA_MARCADO";
    public static final String ENCUESTA_YA_REGISTRADA = "ENCUESTA_YA_REGISTRADA";

    // RECHAZADO
    public static final String DATOS_INVALIDOS = "DATOS_INVALIDOS";
    public static final String EVENTO_NO_ENCONTRADO = "EVENTO_NO_ENCONTRADO";
    public static final String ASISTENTE_NO_ENCONTRADO = "ASISTENTE_NO_ENCONTRADO";
    /** Transitorio: la encuesta llegó antes que su asistencia. El cliente la mantiene PENDIENTE. */
    public static final String ASISTENCIA_NO_ENCONTRADA = "ASISTENCIA_NO_ENCONTRADA";
}
