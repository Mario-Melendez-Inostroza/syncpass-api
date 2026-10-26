package com.duoc.cursos.sync.dto;

import java.util.List;

public record LoteSyncResponse(int aceptados, int duplicados, int rechazados, List<ResultadoSyncItem> resultados) {

    public static LoteSyncResponse de(List<ResultadoSyncItem> resultados) {
        return new LoteSyncResponse(
                contar(resultados, ResultadoEstado.ACEPTADO),
                contar(resultados, ResultadoEstado.DUPLICADO),
                contar(resultados, ResultadoEstado.RECHAZADO),
                resultados);
    }

    private static int contar(List<ResultadoSyncItem> resultados, ResultadoEstado estado) {
        return (int) resultados.stream().filter(r -> r.estado() == estado).count();
    }
}
