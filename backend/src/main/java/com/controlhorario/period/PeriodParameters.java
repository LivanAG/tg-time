package com.controlhorario.period;

import java.time.LocalDate;

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

    Integer maxRemoteDaysMonth();

    Integer openingBalanceMin();
}
