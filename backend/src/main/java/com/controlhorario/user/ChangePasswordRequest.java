package com.controlhorario.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cuerpo de PUT /api/me/password. La política de la nueva contraseña se aplica en el servicio. */
public record ChangePasswordRequest(
        @NotBlank(message = "La contraseña actual es obligatoria")
        @Size(max = 1024, message = "La contraseña actual no es correcta")
        String currentPassword,

        @NotBlank(message = "La nueva contraseña es obligatoria")
        String newPassword) {

    @Override
    public String toString() {
        return "ChangePasswordRequest[***]";
    }
}
