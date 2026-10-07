package com.controlhorario.summary.calc;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.controlhorario.absence.AbsenceType;
import com.controlhorario.calendar.calc.DayType;
import com.controlhorario.calendar.calc.PeriodCalendar;
import com.controlhorario.calendar.calc.PeriodRules;
import com.controlhorario.common.calc.CalcIssue;
import com.controlhorario.common.calc.Minutes;

/**
 * Resumen del periodo (hoja Horas), servicio puro sin Spring:
 * <ul>
 *   <li>horas calendario = suma de jornadaDía de todos los laborables;</li>
 *   <li>margen = horas calendario - horas de convenio;</li>
 *   <li>valor de las vacaciones = jornadas de los días marcados + los que faltan a jornada normal;</li>
 *   <li>horas a recuperar = max(0, valor de las vacaciones - margen);</li>
 *   <li>margen restante = margen - horas de vacaciones disfrutadas;</li>
 *   <li>proyección = saldo inicial + hechas + teóricas restantes - vacaciones sin planificar - convenio.</li>
 * </ul>
 */
public final class PeriodSummaryService {

    public static final String VACATION_OVERPLANNED = "VACATION_OVERPLANNED";

    private final MonthSummaryService monthSummaryService = new MonthSummaryService();

    public PeriodSummary summarize(PeriodCalendar calendar, Collection<com.controlhorario.workday.calc.WorkdayInput> workdays,
            Collection<AbsenceInput> absences, LocalDate today) {
        PeriodRules rules = calendar.rules();

        List<MonthRow> rows = new ArrayList<>();
        int balance = rules.openingBalanceMin();
        int workingDays = 0, normalDays = 0, intensiveDays = 0, calendarMinutes = 0, workedMinutes = 0;
        int theoreticalRemaining = 0, projectedWork = 0, balanceToDate = rules.openingBalanceMin(), bridgeMinutes = 0;
        for (YearMonth month : calendar.months()) {
            MonthSummary ms = monthSummaryService.summarize(calendar, month, workdays, absences, balance, today);
            rows.add(new MonthRow(month, ms.status(), ms.workingDays(), ms.normalDays(), ms.intensiveDays(),
                    ms.calendarMinutes(), ms.vacationDays(), ms.vacationMinutes(), ms.theoreticalMinutes(),
                    ms.workedMinutes(), ms.differenceMinutes(), ms.bridgeMinutes(), ms.closingBalanceMinutes()));
            balance = ms.closingBalanceMinutes();
            workingDays += ms.workingDays();
            normalDays += ms.normalDays();
            intensiveDays += ms.intensiveDays();
            calendarMinutes += ms.calendarMinutes();
            workedMinutes += ms.workedMinutes();
            bridgeMinutes += ms.bridgeMinutes();
            for (DaySummary day : ms.days()) {
                if (day.dayType() == DayType.FUERA_DE_PERIODO) {
                    continue;
                }
                boolean hasWorkday = day.workday() != null;
                boolean past = MonthSummaryService.isPast(day.date(), hasWorkday, today);
                if (past) {
                    balanceToDate += day.workedMinutes() - day.theoreticalMinutes();
                    if (day.absence() != null && day.absence().type() == AbsenceType.PUENTE
                            && day.dayType() == DayType.LABORABLE) {
                        balanceToDate -= day.absence().halfDay() ? Minutes.half(day.dayMinutes()) : day.dayMinutes();
                    }
                    projectedWork += day.workedMinutes();
                } else if (hasWorkday) {
                    // Días futuros ya rellenados (plan): cuentan con lo planificado.
                    projectedWork += day.workedMinutes();
                } else {
                    theoreticalRemaining += day.theoreticalMinutes();
                    projectedWork += day.theoreticalMinutes();
                }
            }
        }

        VacationSummary vacations = vacations(calendar, absences, today);
        int margin = calendarMinutes - rules.agreementMinutes();
        int hoursToRecover = Math.max(0, vacations.valueMinutes() - margin);
        int remainingMargin = margin - vacations.takenMinutes();
        int projection = rules.openingBalanceMin() + projectedWork - vacations.unplannedMinutes()
                - rules.agreementMinutes();

        List<CalcIssue> warnings = new ArrayList<>();
        if (vacations.plannedDays() > vacations.totalDays()) {
            warnings.add(CalcIssue.of(VACATION_OVERPLANNED, "Hay " + vacations.plannedDays()
                    + " días de vacaciones marcados y el convenio da " + vacations.totalDays()));
        }
        return new PeriodSummary(rules.startDate(), rules.endDate(), today, workingDays, normalDays, intensiveDays,
                calendarMinutes, rules.agreementMinutes(), margin, vacations, hoursToRecover, remainingMargin,
                workedMinutes, theoreticalRemaining, projection, rules.openingBalanceMin(), balanceToDate,
                bridgeMinutes, List.copyOf(rows), List.copyOf(warnings));
    }

    static VacationSummary vacations(PeriodCalendar calendar, Collection<AbsenceInput> absences, LocalDate today) {
        PeriodRules rules = calendar.rules();
        double planned = 0, taken = 0, pending = 0;
        int plannedMinutes = 0, takenMinutes = 0, pendingMinutes = 0;
        for (AbsenceInput a : absences) {
            if (a.type() != AbsenceType.VACACIONES || !calendar.isWorkingDay(a.date())) {
                continue;
            }
            int minutes = a.halfDay() ? Minutes.half(calendar.dayMinutes(a.date())) : calendar.dayMinutes(a.date());
            double days = a.halfDay() ? 0.5 : 1;
            planned += days;
            plannedMinutes += minutes;
            if (a.date().isAfter(today)) {
                pending += days;
                pendingMinutes += minutes;
            } else {
                taken += days;
                takenMinutes += minutes;
            }
        }
        double unplanned = Math.max(0, rules.vacationDays() - planned);
        int unplannedMinutes = (int) Math.round(unplanned * rules.normalDayMinutes());
        return new VacationSummary(rules.vacationDays(), planned, plannedMinutes, taken, takenMinutes, pending,
                pendingMinutes, rules.vacationDays() - taken, pendingMinutes + unplannedMinutes, unplanned,
                unplannedMinutes, plannedMinutes + unplannedMinutes);
    }
}
