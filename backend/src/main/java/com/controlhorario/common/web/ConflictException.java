package com.controlhorario.common.web;

/** 409: el recurso cambió (bloqueo optimista) o choca con otro existente. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
