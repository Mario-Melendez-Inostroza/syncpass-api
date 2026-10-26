package com.duoc.cursos.sync.dto;

import com.duoc.cursos.model.Asistente;
import com.duoc.cursos.model.enums.EstadoInscripcion;
import com.duoc.cursos.model.enums.TipoParticipante;

import java.time.Instant;

/** Sin datos personales: solo el código sintético y el tipo de participante (caso §3.2 y §5.1). */
public record AsistenteDto(
        Long id,
        String codigo,
        TipoParticipante tipo,
        EstadoInscripcion estadoInscripcion,
        Instant actualizadoEn) {

    public static AsistenteDto de(Asistente a) {
        return new AsistenteDto(a.getId(), a.getCodigo(), a.getTipo(), a.getEstadoInscripcion(), a.getActualizadoEn());
    }
}
