package com.duoc.cursos.inscripcion.dto;

import com.duoc.cursos.model.Asistente;
import com.duoc.cursos.model.enums.TipoParticipante;

/** Lo que ve el asistente de sí mismo: su código sintético, su tipo y el texto de su QR (el que lee la app del monitor). */
public record MiPerfilDto(Long id, String codigo, TipoParticipante tipo, String qr) {

    public static MiPerfilDto de(Asistente a) {
        return new MiPerfilDto(a.getId(), a.getCodigo(), a.getTipo(), "ASISTENTE_" + a.getId());
    }
}
