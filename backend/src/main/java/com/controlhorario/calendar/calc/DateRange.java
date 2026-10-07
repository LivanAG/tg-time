package com.controlhorario.calendar.calc;

import java.time.LocalDate;

/** Rango de fechas inclusivo. */
public record DateRange(LocalDate start, LocalDate end) {

    public DateRange {
        if (start == null || end == null || end.isBefore(start)) {
            throw new IllegalArgumentException("Rango de fechas inválido: " + start + " - " + end);
        }
    }

    public boolean contains(LocalDate date) {
        return !date.isBefore(start) && !date.isAfter(end);
    }
}
