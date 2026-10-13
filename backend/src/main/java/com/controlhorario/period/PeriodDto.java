package com.controlhorario.period;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * Periodo anual con sus parámetros y rangos de intensiva (equivale a la hoja Horas).
 *
 * @param countFrom  desde qué hora cuenta lo trabajado (lo anterior no suma)
 * @param countUntil hasta qué hora cuenta lo trabajado (lo posterior no suma)
 * @param selected   periodo con el que trabaja el usuario (exactamente uno si tiene alguno)
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
        @JsonFormat(pattern = "HH:mm") LocalTime countFrom,
        @JsonFormat(pattern = "HH:mm") LocalTime countUntil,
        List<IntensiveRangeDto> intensiveRanges,
        boolean selected,
        Long version) {
}
