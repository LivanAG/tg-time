package com.controlhorario.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cuerpo de PUT /api/me. */
public record UpdateProfileRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre admite como máximo 100 caracteres")
        String name,

        @Size(max = 100, message = "La empresa admite como máximo 100 caracteres")
        String company,

        @NotBlank(message = "La zona horaria es obligatoria")
        @ValidTimezone
        String timezone) {
}
