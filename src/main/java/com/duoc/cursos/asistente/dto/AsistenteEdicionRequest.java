package com.duoc.cursos.asistente.dto;

import com.duoc.cursos.model.enums.EstadoInscripcion;
import com.duoc.cursos.model.enums.TipoParticipante;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Cambia el tipo y la inscripción de un asistente. El código es su identidad y no se edita. */
@Data
public class AsistenteEdicionRequest {

    @NotNull
    private TipoParticipante tipo;

    @NotNull
    private EstadoInscripcion estadoInscripcion;
}
