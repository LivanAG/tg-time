package com.controlhorario.summary;

import com.controlhorario.summary.calc.MonthStatus;

/**
 * Fila de la tabla por meses de la hoja Horas.
 *
 * @param month                    "YYYY-MM"
 * @param cumulativeBalanceMinutes saldo acumulado al cerrar el mes
 */
public record MonthRowDto(
        String month,
        MonthStatus status,
        int workingDays,
        int normalDays,
        int intensiveDays,
        int calendarMinutes,
        double vacationDays,
        int vacationMinutes,
        int theoreticalMinutes,
        int workedMinutes,
        int differenceMinutes,
        int bridgeMinutes,
        int cumulativeBalanceMinutes) {
}
