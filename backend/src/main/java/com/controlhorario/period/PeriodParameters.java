package com.controlhorario.period;

import java.time.LocalDate;
import java.time.LocalTime;

/** Parámetros comunes al alta y a la modificación de un periodo. */
interface PeriodParameters {

    String name();

    LocalDate startDate();

    LocalDate endDate();

    Integer agreementMinutes();

    Integer vacationDays();

    Integer normalDayMinutes();

    Integer intensiveDayMinutes();

    Integer breakfastToleranceMin();

    Integer minLunchMin();

    Integer roundingStepMin();

    Integer maxRemotePct();

    Integer openingBalanceMin();

    /** Desde qué hora cuenta lo trabajado; null = sin cambios (07:00 al crear). */
    LocalTime countFrom();

    /** Hasta qué hora cuenta lo trabajado; null = sin cambios (20:00 al crear). */
    LocalTime countUntil();
}
