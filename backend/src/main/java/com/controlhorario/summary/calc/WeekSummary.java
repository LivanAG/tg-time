package com.controlhorario.summary.calc;

import java.time.LocalDate;

/** Subtotal semanal (lunes a domingo, recortado al mes). */
public record WeekSummary(LocalDate weekStart, LocalDate weekEnd, int theoreticalMinutes, int workedMinutes,
        int roundedMinutes) {
}
