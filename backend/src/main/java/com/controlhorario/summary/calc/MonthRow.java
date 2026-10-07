package com.controlhorario.summary.calc;

import java.time.YearMonth;

/** Fila de la tabla por meses de la hoja Horas. */
public record MonthRow(
        YearMonth month,
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
