package com.controlhorario.calendar;

import java.time.LocalDate;
import java.util.UUID;

/** Festivo de un periodo. */
public record HolidayDto(UUID id, LocalDate date, String name, HolidayScope scope) {
}
