package com.controlhorario.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de POST /api/admin/users (alta "por invitación"). {@code role} es USER si no se indica;
 * {@code timezone}, Europe/Madrid. La política de contraseñas se aplica en el servicio.
 */
public record CreateUserRequest(
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
        String timezone,

        Role role) {

    @Override
    public String toString() {
        return "CreateUserRequest[email=" + email + ", role=" + role + "]";
    }
}
