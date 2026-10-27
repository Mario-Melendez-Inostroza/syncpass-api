package com.duoc.cursos.asistente.dto;

import com.duoc.cursos.model.enums.EstadoInscripcion;
import com.duoc.cursos.model.enums.TipoParticipante;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Alta de inscritos por el coordinador (caso §3.1: "consulta listas previas de inscritos"). Se pueden crear varios de una
 * vez: el servidor genera el código sintético de cada uno. No hay ningún campo de datos personales (caso §5.1).
 */
@Data
public class AsistenteAltaRequest {

    public static final int MAX_POR_LOTE = 200;

    @NotNull
    private TipoParticipante tipo;

    /** Si no se indica, quedan INSCRITO (es una inscripción previa). */
    private EstadoInscripcion estadoInscripcion;

    /** Cuántos crear con estos mismos datos. */
    @Min(1)
    @Max(MAX_POR_LOTE)
    private Integer cantidad;
}
