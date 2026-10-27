package com.duoc.cursos.evento.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;

/** Evento o taller de un proyecto (caso §3.2). Los límites coinciden con las columnas de la tabla. */
@Data
public class EventoRequest {

    @NotNull
    private Long proyectoId;

    @NotBlank
    @Size(max = 150)
    private String nombre;

    @NotNull
    private Instant fechaHoraProgramada;

    @Min(1)
    private Integer cuposMaximos;

    /** Mínimo de asistentes para dar por cumplido el quórum. Opcional; no puede superar los cupos máximos. */
    @Min(1)
    private Integer quorumMinimo;

    @Size(max = 200)
    private String recinto;

    @DecimalMin("-90.0")
    @DecimalMax("90.0")
    private Double latitud;

    @DecimalMin("-180.0")
    @DecimalMax("180.0")
    private Double longitud;

    /** Si no se indica, se usa el radio por defecto del dominio (200 m). */
    @Min(1)
    private Integer radioMetros;

    /** Un solo extremo de las coordenadas no sirve para validar la ubicación: van las dos o ninguna. */
    @JsonIgnore
    @AssertTrue(message = "Indica latitud y longitud juntas, o ninguna")
    public boolean isCoordenadasCoherentes() {
        return (latitud == null) == (longitud == null);
    }

    /** Un quórum mínimo mayor que los cupos nunca se podría cumplir. */
    @JsonIgnore
    @AssertTrue(message = "El quórum mínimo no puede superar los cupos máximos")
    public boolean isQuorumCoherente() {
        return quorumMinimo == null || cuposMaximos == null || quorumMinimo <= cuposMaximos;
    }
}
