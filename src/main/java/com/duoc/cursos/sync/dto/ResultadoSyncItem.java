package com.duoc.cursos.sync.dto;

import com.duoc.cursos.model.enums.EstadoValidacion;

/**
 * Resultado individual de un registro del lote.
 *
 * @param indice  posición del registro en la petición (0-based). Permite correlacionar aunque el propio
 *                uuid sea lo que venía inválido.
 * @param id      id del servidor de la asistencia o de la encuesta, cuando existe.
 * @param detalle nombres de los campos inválidos (nunca sus valores), solo en DATOS_INVALIDOS.
 */
public record ResultadoSyncItem(
        int indice,
        String uuid,
        ResultadoEstado estado,
        String motivo,
        Long id,
        EstadoValidacion estadoValidacion,
        Integer distanciaMetros,
        String detalle) {

    public static ResultadoSyncItem aceptado(int indice, String uuid, Long id,
                                             EstadoValidacion estadoValidacion, Integer distanciaMetros) {
        return new ResultadoSyncItem(indice, uuid, ResultadoEstado.ACEPTADO, null, id,
                estadoValidacion, distanciaMetros, null);
    }

    public static ResultadoSyncItem duplicado(int indice, String uuid, String motivo, Long id) {
        return new ResultadoSyncItem(indice, uuid, ResultadoEstado.DUPLICADO, motivo, id, null, null, null);
    }

    public static ResultadoSyncItem rechazado(int indice, String uuid, String motivo) {
        return new ResultadoSyncItem(indice, uuid, ResultadoEstado.RECHAZADO, motivo, null, null, null, null);
    }

    public static ResultadoSyncItem invalido(int indice, String uuid, String camposInvalidos) {
        return new ResultadoSyncItem(indice, uuid, ResultadoEstado.RECHAZADO, MotivoSync.DATOS_INVALIDOS,
                null, null, null, camposInvalidos);
    }
}
