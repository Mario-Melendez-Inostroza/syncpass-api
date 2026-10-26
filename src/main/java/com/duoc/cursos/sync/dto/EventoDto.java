package com.duoc.cursos.sync.dto;

import com.duoc.cursos.model.Evento;

import java.time.Instant;

public record EventoDto(
        Long id,
        Long proyectoId,
        String nombre,
        Instant fechaHoraProgramada,
        Integer cuposMaximos,
        String recinto,
        Double latitud,
        Double longitud,
        Integer radioMetros,
        Instant actualizadoEn) {

    public static EventoDto de(Evento e) {
        return new EventoDto(e.getId(), e.getProyecto().getId(), e.getNombre(), e.getFechaHoraProgramada(),
                e.getCuposMaximos(), e.getRecinto(), e.getLatitud(), e.getLongitud(), e.getRadioMetros(),
                e.getActualizadoEn());
    }
}
