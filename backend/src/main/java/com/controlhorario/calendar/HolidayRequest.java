package com.controlhorario.calendar;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Cuerpo de {@code POST /api/periods/{id}/holidays}. */
public record HolidayRequest(
        @NotNull(message = "La fecha es obligatoria") LocalDate date,
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre admite como máximo 100 caracteres") String name,
        @NotNull(message = "El ámbito es obligatorio") HolidayScope scope) {
}
