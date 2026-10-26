package com.duoc.cursos.sync.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;

/**
 * Una marcación pendiente de subir. Las anotaciones NO se validan con @Valid sobre la lista: un solo
 * ítem inválido tumbaría el lote entero con 400, y el teléfono reintentaría para siempre bloqueando los
 * demás. SyncService valida cada ítem y lo informa como RECHAZADO / DATOS_INVALIDOS.
 *
 * Deliberadamente no hay campo para el usuario que registra: sale del JWT.
 */
@Data
public class AsistenciaSyncItem {

    @NotBlank
    @Size(max = 36)
    private String uuid;

    @NotNull
    private Long eventoId;

    @NotNull
    private Long asistenteId;

    /** Reloj del dispositivo en el momento de la marcación (ISO-8601, UTC). */
    @NotNull
    private Instant marcadoEn;

    @DecimalMin("-90.0")
    @DecimalMax("90.0")
    private Double latitud;

    @DecimalMin("-180.0")
    @DecimalMax("180.0")
    private Double longitud;
}
