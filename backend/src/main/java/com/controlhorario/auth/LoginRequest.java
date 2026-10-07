package com.controlhorario.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cuerpo de POST /api/auth/login. */
public record LoginRequest(
        @NotBlank(message = "El email es obligatorio")
        @Size(max = 254, message = "El email admite como máximo 254 caracteres")
        String email,

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(max = 1024, message = "La contraseña admite como máximo 1024 caracteres")
        String password) {

    /** Nunca incluye la contraseña (por si acaba en un log). */
    @Override
    public String toString() {
        return "LoginRequest[email=" + email + "]";
    }
}
