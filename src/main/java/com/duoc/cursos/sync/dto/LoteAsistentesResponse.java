package com.duoc.cursos.sync.dto;

import java.util.List;

public record LoteAsistentesResponse(int aceptados, int duplicados, int rechazados,
                                     List<ResultadoAsistenteItem> resultados) {

    public static LoteAsistentesResponse de(List<ResultadoAsistenteItem> resultados) {
        return new LoteAsistentesResponse(
                contar(resultados, ResultadoEstado.ACEPTADO),
                contar(resultados, ResultadoEstado.DUPLICADO),
                contar(resultados, ResultadoEstado.RECHAZADO),
                resultados);
    }

    private static int contar(List<ResultadoAsistenteItem> resultados, ResultadoEstado estado) {
        return (int) resultados.stream().filter(r -> r.estado() == estado).count();
    }
}
