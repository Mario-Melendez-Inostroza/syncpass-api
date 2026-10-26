package com.duoc.cursos.sync.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class SincronizarEncuestasRequest {

    public static final int MAX_REGISTROS = 500;

    @NotEmpty
    @Size(max = MAX_REGISTROS)
    private List<EncuestaSyncItem> registros;
}
