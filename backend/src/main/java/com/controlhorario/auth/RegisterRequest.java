package com.controlhorario.auth;

import com.controlhorario.user.ValidTimezone;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de POST /api/auth/register. El rol siempre es USER (no se puede elegir) y la zona horaria
 * por defecto es Europe/Madrid. La política de contraseñas se aplica en el servicio.
 */
public record RegisterRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre admite como máximo 100 caracteres")
        String name,

        @NotBlank(message = "El email es obligatorio")
        @Email(message = "El email no es válido")
        @Size(max = 254, message = "El email admite como máximo 254 caracteres")
        String email,

        @NotBlank(message = "La contraseña es obligatoria")
        String password,

        @Size(max = 100, message = "La empresa admite como máximo 100 caracteres")
        String company,

        @ValidTimezone
        String timezone) {

    /** Nunca incluye la contraseña (por si acaba en un log). */
    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + "]";
    }
}
