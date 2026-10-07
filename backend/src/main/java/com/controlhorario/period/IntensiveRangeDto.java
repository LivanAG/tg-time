package com.controlhorario.period;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

/** Rango de jornada intensiva (fechas inclusivas). */
public record IntensiveRangeDto(
        @NotNull(message = "La fecha de inicio del rango es obligatoria") LocalDate startDate,
        @NotNull(message = "La fecha de fin del rango es obligatoria") LocalDate endDate) {
}
