package com.controlhorario.importexport.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Un día con fichaje del Excel.
 *
 * @param row                   fila de la hoja (numeración de Excel)
 * @param excelWorkedMinutes    Total Día del Excel (columna L)
 * @param computedWorkedMinutes trabajado según WorkdayCalculator con las reglas del periodo (null si no se puede calcular)
 * @param messages              errores (INVALID) y avisos del día
 */
public record ImportDayDto(
        LocalDate date,
        String sheet,
        int row,
        ImportDayStatus status,
        ImportAction action,
        ImportWorkdayDto workday,
        Integer excelWorkedMinutes,
        Integer computedWorkedMinutes,
        List<String> messages) {
}
