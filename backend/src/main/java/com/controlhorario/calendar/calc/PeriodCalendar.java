package com.controlhorario.calendar.calc;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Calendario de un periodo: qué días son laborables y cuánto dura su jornada.
 * <ul>
 *   <li>laborable = dentro del periodo, ni fin de semana ni festivo;</li>
 *   <li>jornadaDía = jornada intensiva si la fecha cae en un rango de intensiva; si no, normal.</li>
 * </ul>
 */
public final class PeriodCalendar {

    private final PeriodRules rules;

    public PeriodCalendar(PeriodRules rules) {
        this.rules = rules;
    }

    public PeriodRules rules() {
        return rules;
    }

    public boolean inPeriod(LocalDate date) {
        return !date.isBefore(rules.startDate()) && !date.isAfter(rules.endDate());
    }

    public DayType dayType(LocalDate date) {
        if (!inPeriod(date)) {
            return DayType.FUERA_DE_PERIODO;
        }
        if (rules.holidays().containsKey(date)) {
            return DayType.FESTIVO;
        }
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            return DayType.FIN_DE_SEMANA;
        }
        return DayType.LABORABLE;
    }

    public boolean isWorkingDay(LocalDate date) {
        return dayType(date) == DayType.LABORABLE;
    }

    public boolean isIntensive(LocalDate date) {
        return rules.intensiveRanges().stream().anyMatch(r -> r.contains(date));
    }

    /** Jornada teórica del día: 0 si no es laborable. */
    public int dayMinutes(LocalDate date) {
        if (!isWorkingDay(date)) {
            return 0;
        }
        return isIntensive(date) ? rules.intensiveDayMinutes() : rules.normalDayMinutes();
    }

    public Optional<String> holidayName(LocalDate date) {
        return Optional.ofNullable(rules.holidays().get(date));
    }

    /** Meses que toca el periodo, en orden. */
    public List<YearMonth> months() {
        List<YearMonth> months = new ArrayList<>();
        for (YearMonth m = YearMonth.from(rules.startDate()); !m.isAfter(YearMonth.from(rules.endDate())); m = m.plusMonths(1)) {
            months.add(m);
        }
        return months;
    }

    /** Todos los días naturales de un mes (estén o no dentro del periodo). */
    public static List<LocalDate> datesOf(YearMonth month) {
        List<LocalDate> dates = new ArrayList<>(month.lengthOfMonth());
        for (int d = 1; d <= month.lengthOfMonth(); d++) {
            dates.add(month.atDay(d));
        }
        return dates;
    }
}
