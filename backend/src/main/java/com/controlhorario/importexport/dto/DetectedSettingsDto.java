package com.controlhorario.importexport.dto;

/** Parámetros del periodo deducidos del Excel (null si no aparecen). Duraciones en minutos. */
public record DetectedSettingsDto(
        Integer breakfastToleranceMin,
        Integer minLunchMin,
        Integer maxRemotePct,
        Integer normalDayMinutes,
        Integer intensiveDayMinutes,
        Integer vacationDays,
        Integer agreementMinutes) {
}
