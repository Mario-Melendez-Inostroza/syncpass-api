package com.duoc.cursos.proyecto.dto;

import com.duoc.cursos.model.enums.EstadoProyecto;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/** Ficha de un proyecto comunitario (caso §3.2). Los límites coinciden con las columnas de la tabla. */
@Data
public class ProyectoRequest {

    @NotBlank
    @Size(max = 150)
    private String titulo;

    @Size(max = 1000)
    private String descripcion;

    @NotBlank
    @Size(max = 80)
    private String areaTematica;

    @NotBlank
    @Size(max = 120)
    private String unidadVecinal;

    @NotNull
    private LocalDate fechaInicio;

    private LocalDate fechaTermino;

    /** Si no se indica, un proyecto nuevo queda PLANIFICADO. */
    private EstadoProyecto estado;

    @JsonIgnore
    @AssertTrue(message = "La fecha de término no puede ser anterior a la de inicio")
    public boolean isFechasCoherentes() {
        return fechaInicio == null || fechaTermino == null || !fechaTermino.isBefore(fechaInicio);
    }
}
