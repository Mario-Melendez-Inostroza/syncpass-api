package com.duoc.cursos.sync.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Envoltorio del lote. Es un objeto y no una lista desnuda porque la validación de una lista como
 * cuerpo lanza HandlerMethodValidationException, y así sale como MethodArgumentNotValidException (400).
 */
@Data
public class SincronizarAsistenciasRequest {

    public static final int MAX_REGISTROS = 500;

    @NotEmpty
    @Size(max = MAX_REGISTROS)
    private List<AsistenciaSyncItem> registros;
}
