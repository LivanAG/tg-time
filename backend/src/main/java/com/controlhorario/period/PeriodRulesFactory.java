package com.controlhorario.period;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import com.controlhorario.calendar.Holiday;
import com.controlhorario.calendar.HolidayRepository;
import com.controlhorario.calendar.calc.DateRange;
import com.controlhorario.calendar.calc.PeriodCalendar;
import com.controlhorario.calendar.calc.PeriodRules;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Traduce un periodo de la base de datos (con sus rangos de intensiva y festivos) a reglas de cálculo. */
@Component
public class PeriodRulesFactory {

    private final IntensiveRangeRepository intensiveRanges;
    private final HolidayRepository holidays;

    public PeriodRulesFactory(IntensiveRangeRepository intensiveRanges, HolidayRepository holidays) {
        this.intensiveRanges = intensiveRanges;
        this.holidays = holidays;
    }

    @Transactional(readOnly = true)
    public PeriodRules rulesFor(WorkPeriod period) {
        Map<LocalDate, String> holidayNames = new LinkedHashMap<>();
        for (Holiday h : holidays.findByPeriodIdOrderByDate(period.getId())) {
            holidayNames.put(h.getDate(), h.getName());
        }
        return new PeriodRules(period.getStartDate(), period.getEndDate(), period.getAgreementMinutes(),
                period.getVacationDays(), period.getNormalDayMinutes(), period.getIntensiveDayMinutes(),
                period.getBreakfastToleranceMin(), period.getMinLunchMin(), period.getRoundingStepMin(),
                period.getMaxRemotePct(), period.getMaxRemoteDaysMonth(), period.getOpeningBalanceMin(),
                intensiveRanges.findByPeriodIdOrderByStartDate(period.getId()).stream()
                        .map(r -> new DateRange(r.getStartDate(), r.getEndDate()))
                        .toList(),
                holidayNames);
    }

    public PeriodCalendar calendarFor(WorkPeriod period) {
        return new PeriodCalendar(rulesFor(period));
    }
}
