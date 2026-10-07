package com.controlhorario.common.web;

/** Recurso inexistente o de otro usuario: siempre 404, nunca 403 (protección IDOR). */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
