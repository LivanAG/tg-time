package com.controlhorario.absence;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Cuerpo de {@code PUT /api/absences/{date}}. {@code halfDay} null equivale a false. */
public record AbsenceRequest(
        @NotNull(message = "El tipo de ausencia es obligatorio") AbsenceType type,
        Boolean halfDay,
        @Size(max = 500, message = "La nota admite como máximo 500 caracteres") String note) {
}
