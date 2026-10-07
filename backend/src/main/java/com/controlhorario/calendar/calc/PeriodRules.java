package com.controlhorario.calendar.calc;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Parámetros de un periodo anual, sin dependencias de JPA: entrada de todos los cálculos.
 * Todas las duraciones en minutos.
 */
public record PeriodRules(
        LocalDate startDate,
        LocalDate endDate,
        int agreementMinutes,
        int vacationDays,
        int normalDayMinutes,
        int intensiveDayMinutes,
        int breakfastToleranceMin,
        int minLunchMin,
        int roundingStepMin,
        int maxRemotePct,
        int maxRemoteDaysMonth,
        int openingBalanceMin,
        List<DateRange> intensiveRanges,
        Map<LocalDate, String> holidays) {

    public PeriodRules {
        intensiveRanges = List.copyOf(intensiveRanges);
        holidays = Map.copyOf(holidays);
    }
}
