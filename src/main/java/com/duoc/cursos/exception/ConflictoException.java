package com.duoc.cursos.exception;

/** La operación choca con el estado actual del dominio (p. ej. borrar un evento que ya tiene asistencias). Se traduce a 409. */
public class ConflictoException extends RuntimeException {

    public ConflictoException(String mensaje) {
        super(mensaje);
    }
}
