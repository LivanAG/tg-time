package com.controlhorario.summary.calc;

import java.time.YearMonth;
import java.util.List;

import com.controlhorario.common.calc.CalcIssue;

/**
 * Cierre de un mes (equivale al pie de la hoja mensual). Solo cuentan los días dentro del periodo.
 *
 * @param calendarMinutes         suma de jornadaDía de los laborables (Horas Mes del Excel)
 * @param theoreticalMinutes      jornada de los laborables sin ausencia (C46); medio día = mitad
 * @param workedMinutes           trabajado real (C47)
 * @param roundedMinutes          suma de redondeados = round(trabajado)
 * @param differenceMinutes       trabajado - teóricas, con signo (+ sobra, - falta)
 * @param theoreticalToDateMinutes teóricas de los días ya pasados (hoy cuenta si está fichado)
 * @param openingBalanceMinutes   saldo acumulado al empezar el mes
 * @param closingBalanceMinutes   apertura + diferencia - puentes recuperables
 * @param remotePct               minutos en casa / trabajado * 100
 * @param remoteDays              días con ubicación CASA o MIXTO (informativo: el límite es solo el %)
 */
public record MonthSummary(
        YearMonth month,
        MonthStatus status,
        int workingDays,
        int normalDays,
        int intensiveDays,
        int calendarMinutes,
        int theoreticalMinutes,
        double vacationDays,
        int vacationMinutes,
        double bridgeDays,
        int bridgeMinutes,
        int workedMinutes,
        int roundedMinutes,
        int differenceMinutes,
        int theoreticalToDateMinutes,
        int workedToDateMinutes,
        int differenceToDateMinutes,
        int openingBalanceMinutes,
        int closingBalanceMinutes,
        int remoteMinutes,
        int officeMinutes,
        double remotePct,
        int remoteDays,
        List<CalcIssue> warnings,
        List<WeekSummary> weeks,
        List<DaySummary> days) {
}
