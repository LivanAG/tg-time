package com.controlhorario.importexport.excel;

/**
 * Parámetros del periodo que se deducen del Excel (cabeceras de las hojas mensuales y hoja Horas).
 * Cada uno es null si no aparece en el fichero. Duraciones en minutos.
 */
public record DetectedSettings(
        Integer breakfastToleranceMin,
        Integer minLunchMin,
        Integer maxRemotePct,
        Integer normalDayMinutes,
        Integer intensiveDayMinutes,
        Integer vacationDays,
        Integer agreementMinutes) {
}
