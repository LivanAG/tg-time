package com.controlhorario.summary.calc;

import java.time.LocalDate;
import java.util.List;

import com.controlhorario.common.calc.CalcIssue;

/**
 * Equivalente a la hoja Horas.
 *
 * @param calendarMinutes            suma de jornadaDía de todos los laborables (horas calendario)
 * @param marginMinutes              horas calendario - horas de convenio
 * @param hoursToRecoverMinutes      max(0, valor de las vacaciones - margen)
 * @param remainingMarginMinutes     margen - horas de vacaciones disfrutadas
 * @param workedMinutes              horas hechas en el periodo
 * @param theoreticalRemainingMinutes teóricas de los días que quedan sin fichar
 * @param projectionMinutes          saldo inicial + hechas + teóricas restantes - vacaciones sin planificar - convenio
 *                                   (positivo sobra, negativo falta)
 * @param balanceToDateMinutes       saldo inicial + (trabajado - teóricas) hasta hoy - puentes hasta hoy
 */
public record PeriodSummary(
        LocalDate startDate,
        LocalDate endDate,
        LocalDate today,
        int workingDays,
        int normalDays,
        int intensiveDays,
        int calendarMinutes,
        int agreementMinutes,
        int marginMinutes,
        VacationSummary vacations,
        int hoursToRecoverMinutes,
        int remainingMarginMinutes,
        int workedMinutes,
        int theoreticalRemainingMinutes,
        int projectionMinutes,
        int openingBalanceMinutes,
        int balanceToDateMinutes,
        int bridgeMinutes,
        List<MonthRow> months,
        List<CalcIssue> warnings) {
}
