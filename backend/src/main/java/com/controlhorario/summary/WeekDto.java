package com.controlhorario.summary;

import java.time.LocalDate;

/** Subtotal semanal (lunes a domingo, recortado al mes). */
public record WeekDto(
        LocalDate weekStart,
        LocalDate weekEnd,
        int theoreticalMinutes,
        int workedMinutes,
        int roundedMinutes) {
}
