package com.duoc.cursos.sync.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;

/** Una encuesta pendiente de subir. Se valida ítem a ítem, igual que AsistenciaSyncItem. */
@Data
public class EncuestaSyncItem {

    @NotBlank
    @Size(max = 36)
    private String uuid;

    /** uuid de la asistencia a la que valora (la que el teléfono generó al marcar). */
    @NotBlank
    @Size(max = 36)
    private String asistenciaUuid;

    @NotNull
    @Min(1)
    @Max(5)
    private Integer satisfaccion;

    @NotNull
    @Min(1)
    @Max(5)
    private Integer utilidad;

    @Size(max = 1000)
    private String comentarios;

    @NotNull
    private Instant enviadaEn;
}
