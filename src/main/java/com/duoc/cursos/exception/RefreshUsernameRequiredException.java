package com.duoc.cursos.exception;

/**
 * Se lanza cuando llega una petición de refresh sin identificar al usuario y el App Client de Cognito
 * tiene client secret.
 *
 * Limitación real de Cognito: en el flujo REFRESH_TOKEN_AUTH, si el App Client tiene secret, hay que
 * enviar SECRET_HASH, que es HMAC-SHA256(username + clientId). El refresh token de Cognito es opaco
 * (no es un JWT), así que el username NO puede deducirse de él: el cliente debe enviarlo.
 *
 * Se resuelve de una de estas dos formas:
 *  - el cliente añade "email" al cuerpo de POST /api/auth/refresh, o
 *  - el App Client se configura sin client secret (entonces no se exige SECRET_HASH).
 */
public class RefreshUsernameRequiredException extends RuntimeException {

    public RefreshUsernameRequiredException() {
        super("El App Client tiene client secret: la renovación requiere identificar al usuario");
    }
}
