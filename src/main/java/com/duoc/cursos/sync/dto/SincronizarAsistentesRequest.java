package com.duoc.cursos.sync.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** Envoltorio del lote de altas en terreno (objeto y no lista desnuda, por la misma razón que las asistencias). */
@Data
public class SincronizarAsistentesRequest {

    public static final int MAX_REGISTROS = 500;

    @NotEmpty
    @Size(max = MAX_REGISTROS)
    private List<AsistenteSyncItem> registros;
}
