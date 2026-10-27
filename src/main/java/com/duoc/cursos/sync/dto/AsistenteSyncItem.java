package com.duoc.cursos.sync.dto;

import com.duoc.cursos.model.enums.TipoParticipante;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Un asistente dado de alta en terreno (caso §3.1: el monitor anota a quienes llegan sin inscripción previa).
 *
 * Solo lleva el uuid que genera el teléfono y el tipo. El código sintético y el estado de inscripción los
 * decide el servidor, y no hay ningún campo de datos personales (caso §5.1). Se valida ítem por ítem, igual
 * que las asistencias, para que uno inválido no tumbe el lote.
 */
@Data
public class AsistenteSyncItem {

    /** Un UUID (hex y guiones). El código del asistente se deriva de él, así que se restringe el alfabeto. */
    @NotBlank
    @Size(max = 36)
    @Pattern(regexp = "^[0-9a-fA-F-]{8,36}$")
    private String uuid;

    @NotNull
    private TipoParticipante tipo;
}
