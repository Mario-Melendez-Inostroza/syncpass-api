package com.duoc.cursos.sync.dto;

import com.duoc.cursos.model.Proyecto;
import com.duoc.cursos.model.enums.EstadoProyecto;

import java.time.Instant;
import java.time.LocalDate;

public record ProyectoDto(
        Long id,
        String titulo,
        String descripcion,
        String areaTematica,
        String unidadVecinal,
        LocalDate fechaInicio,
        LocalDate fechaTermino,
        EstadoProyecto estado,
        Instant actualizadoEn) {

    public static ProyectoDto de(Proyecto p) {
        return new ProyectoDto(p.getId(), p.getTitulo(), p.getDescripcion(), p.getAreaTematica(),
                p.getUnidadVecinal(), p.getFechaInicio(), p.getFechaTermino(), p.getEstado(), p.getActualizadoEn());
    }
}
