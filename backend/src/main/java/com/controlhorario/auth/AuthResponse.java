package com.controlhorario.auth;

import com.controlhorario.user.UserDto;

/** Respuesta de login y refresh. {@code expiresIn} en segundos. El refresh token va solo en la cookie. */
public record AuthResponse(String accessToken, long expiresIn, UserDto user) {

    /** Nunca incluye el token (por si acaba en un log). */
    @Override
    public String toString() {
        return "AuthResponse[expiresIn=" + expiresIn + ", user=" + user + "]";
    }
}
