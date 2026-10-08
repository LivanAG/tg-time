package com.controlhorario.period;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Periodo anual con sus parámetros y rangos de intensiva (equivale a la hoja Horas).
 *
 * @param selected periodo con el que trabaja el usuario (exactamente uno si tiene alguno)
 */
public record PeriodDto(
        UUID id,
        String name,
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
        int openingBalanceMin,
        List<IntensiveRangeDto> intensiveRanges,
        boolean selected,
        Long version) {
}
