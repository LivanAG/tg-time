package com.controlhorario.summary;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.controlhorario.workday.IssueDto;

/** Resumen del periodo (hoja Horas): tabla por meses, margen, vacaciones, saldo y proyección. */
public record PeriodSummaryDto(
        UUID periodId,
        String name,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate today,
        int workingDays,
        int normalDays,
        int intensiveDays,
        int calendarMinutes,
        int agreementMinutes,
        int marginMinutes,
        VacationSummaryDto vacations,
        int hoursToRecoverMinutes,
        int remainingMarginMinutes,
        int workedMinutes,
        int theoreticalRemainingMinutes,
        int projectionMinutes,
        int openingBalanceMinutes,
        int balanceToDateMinutes,
        int bridgeMinutes,
        List<MonthRowDto> months,
        List<IssueDto> warnings) {
}
