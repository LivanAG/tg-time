package com.controlhorario.summary.calc;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.controlhorario.absence.AbsenceType;
import com.controlhorario.calendar.calc.DayType;
import com.controlhorario.calendar.calc.PeriodCalendar;
import com.controlhorario.calendar.calc.PeriodRules;
import com.controlhorario.common.calc.CalcIssue;
import com.controlhorario.common.calc.Minutes;
import com.controlhorario.workday.Location;
import com.controlhorario.workday.calc.WorkdayCalculator;
import com.controlhorario.workday.calc.WorkdayInput;
import com.controlhorario.workday.calc.WorkdayResult;

/**
 * Resumen de un mes (servicio puro, sin Spring). Nivel mes de la especificación:
 * <ul>
 *   <li>teóricas = suma de jornadaDía de los laborables sin ausencia (medio día = mitad);</li>
 *   <li>vacaciones = jornadaDía de los días VACACIONES; puentes = jornadaDía de los PUENTE;</li>
 *   <li>diferencia = trabajado - teóricas, con signo;</li>
 *   <li>saldo de cierre = saldo de apertura + diferencia - puentes recuperables;</li>
 *   <li>teletrabajo: % casa = minutos en casa / trabajado (el límite es solo este %); días casa = días CASA o
 *   MIXTO, informativo.</li>
 * </ul>
 */
public final class MonthSummaryService {

    public static final String MISSING_RECORD = "MISSING_RECORD";
    public static final String REMOTE_PCT_EXCEEDED = "REMOTE_PCT_EXCEEDED";

    public MonthSummary summarize(PeriodCalendar calendar, YearMonth month, Collection<WorkdayInput> workdays,
            Collection<AbsenceInput> absences, int openingBalanceMinutes, LocalDate today) {
        PeriodRules rules = calendar.rules();
        WorkdayCalculator calculator = new WorkdayCalculator(rules.breakfastToleranceMin(), rules.minLunchMin());
        RoundingService rounding = new RoundingService(rules.roundingStepMin());

        Map<LocalDate, WorkdayInput> workdayByDate = new HashMap<>();
        for (WorkdayInput w : workdays) {
            if (YearMonth.from(w.date()).equals(month) && calendar.inPeriod(w.date())) {
                workdayByDate.put(w.date(), w);
            }
        }
        Map<LocalDate, AbsenceInput> absenceByDate = new HashMap<>();
        for (AbsenceInput a : absences) {
            if (YearMonth.from(a.date()).equals(month) && calendar.inPeriod(a.date())) {
                absenceByDate.put(a.date(), a);
            }
        }

        List<LocalDate> dates = PeriodCalendar.datesOf(month);
        int n = dates.size();
        DayType[] types = new DayType[n];
        int[] dayMinutes = new int[n];
        int[] theoretical = new int[n];
        int[] worked = new int[n];
        WorkdayResult[] results = new WorkdayResult[n];

        int workingDays = 0, normalDays = 0, intensiveDays = 0, calendarMinutes = 0, theoreticalMinutes = 0;
        double vacationDays = 0, bridgeDays = 0;
        int vacationMinutes = 0, bridgeMinutes = 0, workedMinutes = 0;
        int theoreticalToDate = 0, workedToDate = 0, bridgeToDate = 0;
        int remoteMinutes = 0, officeMinutes = 0, remoteDays = 0;

        for (int i = 0; i < n; i++) {
            LocalDate date = dates.get(i);
            types[i] = calendar.dayType(date);
            dayMinutes[i] = calendar.dayMinutes(date);
            boolean laborable = types[i] == DayType.LABORABLE;
            if (laborable) {
                workingDays++;
                calendarMinutes += dayMinutes[i];
                if (calendar.isIntensive(date)) {
                    intensiveDays++;
                } else {
                    normalDays++;
                }
            }
            AbsenceInput absence = absenceByDate.get(date);
            int absenceMinutes = 0;
            double absenceDays = 0;
            if (absence != null && laborable) {
                absenceMinutes = absence.halfDay() ? Minutes.half(dayMinutes[i]) : dayMinutes[i];
                absenceDays = absence.halfDay() ? 0.5 : 1;
                if (absence.type() == AbsenceType.VACACIONES) {
                    vacationMinutes += absenceMinutes;
                    vacationDays += absenceDays;
                } else if (absence.type() == AbsenceType.PUENTE) {
                    bridgeMinutes += absenceMinutes;
                    bridgeDays += absenceDays;
                }
            }
            theoretical[i] = dayMinutes[i] - absenceMinutes;
            theoreticalMinutes += theoretical[i];

            WorkdayInput workday = workdayByDate.get(date);
            if (workday != null) {
                results[i] = calculator.calculate(workday);
                worked[i] = results[i].workedMinutes();
                workedMinutes += worked[i];
                remoteMinutes += results[i].remoteMinutes();
                officeMinutes += results[i].officeMinutes();
                if (workday.location() == Location.CASA || workday.location() == Location.MIXTO) {
                    remoteDays++;
                }
            }
            if (isPast(date, workday != null, today)) {
                theoreticalToDate += theoretical[i];
                workedToDate += worked[i];
                if (absence != null && absence.type() == AbsenceType.PUENTE && laborable) {
                    bridgeToDate += absenceMinutes;
                }
            }
        }

        List<Integer> roundedList = rounding.distribute(java.util.Arrays.stream(worked).boxed().toList());
        List<DaySummary> days = new ArrayList<>(n);
        int accumulatedWorked = 0;
        for (int i = 0; i < n; i++) {
            LocalDate date = dates.get(i);
            accumulatedWorked += worked[i];
            WorkdayInput workday = workdayByDate.get(date);
            AbsenceInput absence = absenceByDate.get(date);
            int rounded = roundedList.get(i);
            List<CalcIssue> warnings = new ArrayList<>();
            if (results[i] != null) {
                warnings.addAll(results[i].warnings());
            } else if (types[i] == DayType.LABORABLE && date.isBefore(today)
                    && (absence == null || absence.halfDay())) {
                warnings.add(CalcIssue.of(MISSING_RECORD, "Día laborable sin fichaje ni ausencia"));
            }
            days.add(new DaySummary(date, types[i], calendar.inPeriod(date) && calendar.isIntensive(date),
                    dayMinutes[i], calendar.holidayName(date).orElse(null), absence, workday, results[i],
                    theoretical[i], worked[i], rounded, theoreticalMinutes - accumulatedWorked, List.copyOf(warnings)));
        }

        double remotePct = workedMinutes > 0 ? Math.round(remoteMinutes * 1000.0 / workedMinutes) / 10.0 : 0;
        List<CalcIssue> monthWarnings = new ArrayList<>();
        if (remotePct > rules.maxRemotePct()) {
            monthWarnings.add(CalcIssue.of(REMOTE_PCT_EXCEEDED, "Teletrabajo del " + remotePct
                    + " %: supera el máximo del " + rules.maxRemotePct() + " %"));
        }

        int difference = workedMinutes - theoreticalMinutes;
        int differenceToDate = workedToDate - theoreticalToDate;
        int roundedMinutes = rounding.round(workedMinutes);
        return new MonthSummary(month, status(month, today), workingDays, normalDays, intensiveDays, calendarMinutes,
                theoreticalMinutes, vacationDays, vacationMinutes, bridgeDays, bridgeMinutes, workedMinutes,
                roundedMinutes, difference, theoreticalToDate, workedToDate, differenceToDate, openingBalanceMinutes,
                openingBalanceMinutes + difference - bridgeMinutes, remoteMinutes, officeMinutes, remotePct, remoteDays,
                List.copyOf(monthWarnings), weeks(days), List.copyOf(days));
    }

    /** Un día cuenta como pasado si es anterior a hoy, u hoy si ya está fichado. */
    static boolean isPast(LocalDate date, boolean hasWorkday, LocalDate today) {
        return date.isBefore(today) || (date.equals(today) && hasWorkday);
    }

    static MonthStatus status(YearMonth month, LocalDate today) {
        YearMonth current = YearMonth.from(today);
        if (month.isBefore(current)) {
            return MonthStatus.PAST;
        }
        return month.equals(current) ? MonthStatus.CURRENT : MonthStatus.FUTURE;
    }

    private static List<WeekSummary> weeks(List<DaySummary> days) {
        List<WeekSummary> weeks = new ArrayList<>();
        LocalDate weekStart = null;
        LocalDate weekEnd = null;
        int theoretical = 0, worked = 0, rounded = 0;
        for (DaySummary day : days) {
            if (weekStart == null || day.date().getDayOfWeek() == DayOfWeek.MONDAY) {
                if (weekStart != null) {
                    weeks.add(new WeekSummary(weekStart, weekEnd, theoretical, worked, rounded));
                }
                weekStart = day.date();
                theoretical = 0;
                worked = 0;
                rounded = 0;
            }
            weekEnd = day.date();
            theoretical += day.theoreticalMinutes();
            worked += day.workedMinutes();
            rounded += day.roundedMinutes();
        }
        if (weekStart != null) {
            weeks.add(new WeekSummary(weekStart, weekEnd, theoretical, worked, rounded));
        }
        return List.copyOf(weeks);
    }
}
