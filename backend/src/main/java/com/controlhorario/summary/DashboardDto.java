package com.controlhorario.summary;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.controlhorario.workday.IssueDto;
import com.controlhorario.workday.WorkdayDto;

/**
 * Indicadores de la portada con el periodo que contiene hoy (en la zona horaria del usuario).
 * Sin periodo: {@code period}, {@code currentMonth} y {@code vacations} son null y los minutos 0.
 */
public record DashboardDto(
        LocalDate today,
        PeriodRef period,
        int balanceToDateMinutes,
        CurrentMonth currentMonth,
        Vacations vacations,
        int hoursToRecoverMinutes,
        int projectionMinutes,
        boolean todayIsWorkingDay,
        int todayDayMinutes,
        WorkdayDto todayWorkday) {

    public record PeriodRef(UUID id, String name, LocalDate startDate, LocalDate endDate) {
    }

    /** @param month "YYYY-MM" */
    public record CurrentMonth(
            String month,
            int theoreticalMinutes,
            int workedMinutes,
            int differenceMinutes,
            int theoreticalToDateMinutes,
            int workedToDateMinutes,
            int differenceToDateMinutes,
            double remotePct,
            int remoteDays,
            int maxRemotePct,
            int maxRemoteDaysMonth,
            int imputationWarningDays,
            List<IssueDto> warnings) {
    }

    public record Vacations(int totalDays, double takenDays, double remainingDays, double pendingPlannedDays) {
    }
}
