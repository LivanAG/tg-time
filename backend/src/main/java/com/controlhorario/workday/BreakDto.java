package com.controlhorario.workday;

import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * Pausa de una jornada. La validación (tipo y horas obligatorios, dentro de la jornada, sin
 * solaparse) la hace el cálculo del día y devuelve el error en {@code breaks[i]}.
 */
public record BreakDto(
        BreakType type,
        @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        @JsonFormat(pattern = "HH:mm") LocalTime endTime) {
}
