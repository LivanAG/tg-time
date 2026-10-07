package com.controlhorario.summary;

import java.util.List;
import java.util.UUID;

import com.controlhorario.summary.calc.MonthStatus;
import com.controlhorario.workday.IssueDto;

/**
 * Resumen de un mes (hoja mensual con su pie). Diferencias y saldos con signo: positivo sobra,
 * negativo falta.
 *
 * @param month "YYYY-MM"
 */
public record MonthSummaryDto(
        UUID periodId,
        String month,
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
        int maxRemotePct,
        int maxRemoteDaysMonth,
        Integer jiraMinutes,
        Integer izertiaMinutes,
        int imputationWarningDays,
        List<IssueDto> warnings,
        List<WeekDto> weeks,
        List<DayDto> days) {
}
