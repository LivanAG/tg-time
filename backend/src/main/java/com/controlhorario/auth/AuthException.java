package com.controlhorario.auth;

import org.springframework.http.HttpStatus;

/**
 * Error de autenticación de /api/auth/* con su estado HTTP (401 credenciales o sesión, 403 registro
 * cerrado). Si {@code clearCookie}, la respuesta borra además la cookie de refresco.
 */
public class AuthException extends RuntimeException {

    private final HttpStatus status;
    private final boolean clearCookie;

    public AuthException(HttpStatus status, String message, boolean clearCookie) {
        super(message);
        this.status = status;
        this.clearCookie = clearCookie;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public boolean isClearCookie() {
        return clearCookie;
    }
}
