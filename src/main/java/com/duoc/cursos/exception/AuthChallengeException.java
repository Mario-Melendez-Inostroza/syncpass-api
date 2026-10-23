package com.duoc.cursos.exception;

/**
 * Cognito respondió al login con un reto de autenticación (p. ej. NEW_PASSWORD_REQUIRED o MFA)
 * que este backend no implementa, por lo que no hay tokens que devolver.
 */
public class AuthChallengeException extends RuntimeException {

    private final String challengeName;

    public AuthChallengeException(String challengeName) {
        super("Reto de autenticación no soportado: " + challengeName);
        this.challengeName = challengeName;
    }

    public String getChallengeName() {
        return challengeName;
    }
}
