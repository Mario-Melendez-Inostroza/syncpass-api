package com.duoc.cursos.sync.dto;

/**
 * Resultado de un alta del lote. A diferencia de {@link ResultadoSyncItem} devuelve el id y el código que
 * asignó el servidor: el teléfono los necesita para reemplazar su asistente provisional.
 *
 * @param detalle nombres de los campos inválidos (nunca sus valores), solo en DATOS_INVALIDOS.
 */
public record ResultadoAsistenteItem(
        int indice,
        String uuid,
        ResultadoEstado estado,
        String motivo,
        Long id,
        String codigo,
        String detalle) {

    public static ResultadoAsistenteItem aceptado(int indice, String uuid, Long id, String codigo) {
        return new ResultadoAsistenteItem(indice, uuid, ResultadoEstado.ACEPTADO, null, id, codigo, null);
    }

    public static ResultadoAsistenteItem duplicado(int indice, String uuid, Long id, String codigo) {
        return new ResultadoAsistenteItem(indice, uuid, ResultadoEstado.DUPLICADO, MotivoSync.UUID_YA_REGISTRADO,
                id, codigo, null);
    }

    public static ResultadoAsistenteItem invalido(int indice, String uuid, String camposInvalidos) {
        return new ResultadoAsistenteItem(indice, uuid, ResultadoEstado.RECHAZADO, MotivoSync.DATOS_INVALIDOS,
                null, null, camposInvalidos);
    }
}
