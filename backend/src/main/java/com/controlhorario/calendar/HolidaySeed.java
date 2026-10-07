package com.controlhorario.calendar;

import java.time.LocalDate;

/** Festivo de la lista de precarga (todavía no asociado a ningún periodo). */
public record HolidaySeed(LocalDate date, String name, HolidayScope scope) {
}
