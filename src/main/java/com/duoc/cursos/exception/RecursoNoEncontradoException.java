package com.duoc.cursos.exception;

/** Un recurso del dominio SyncPass (p. ej. un evento) no existe. Se traduce a 404. */
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
