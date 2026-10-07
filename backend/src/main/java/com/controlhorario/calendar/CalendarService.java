package com.controlhorario.calendar;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.controlhorario.absence.Absence;
import com.controlhorario.absence.AbsenceMapper;
import com.controlhorario.absence.AbsenceRepository;
import com.controlhorario.calendar.calc.PeriodCalendar;
import com.controlhorario.period.PeriodRulesFactory;
import com.controlhorario.period.PeriodService;
import com.controlhorario.period.WorkPeriod;
import com.controlhorario.workday.WorkdayRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Calendario de un periodo: tipo y jornada de cada día, con sus ausencias y si hay fichaje. */
@Service
public class CalendarService {

    private final PeriodService periods;
    private final PeriodRulesFactory rulesFactory;
    private final AbsenceRepository absences;
    private final WorkdayRepository workdays;
    private final AbsenceMapper absenceMapper;

    public CalendarService(PeriodService periods, PeriodRulesFactory rulesFactory, AbsenceRepository absences,
            WorkdayRepository workdays, AbsenceMapper absenceMapper) {
        this.periods = periods;
        this.rulesFactory = rulesFactory;
        this.absences = absences;
        this.workdays = workdays;
        this.absenceMapper = absenceMapper;
    }

    @Transactional(readOnly = true)
    public List<CalendarDayDto> calendar(UUID userId, UUID periodId) {
        WorkPeriod period = periods.requireOwned(userId, periodId);
        PeriodCalendar calendar = rulesFactory.calendarFor(period);
        LocalDate start = period.getStartDate();
        LocalDate end = period.getEndDate();

        Map<LocalDate, Absence> absenceByDate = new HashMap<>();
        for (Absence a : absences.findByUserIdAndDateBetweenOrderByDate(userId, start, end)) {
            absenceByDate.put(a.getDate(), a);
        }
        Set<LocalDate> workdayDates = new HashSet<>(workdays.findDatesBetween(userId, start, end));

        List<CalendarDayDto> days = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            Absence absence = absenceByDate.get(date);
            days.add(new CalendarDayDto(date, calendar.dayType(date), calendar.isIntensive(date),
                    calendar.dayMinutes(date), calendar.holidayName(date).orElse(null),
                    absence == null ? null : absenceMapper.toDto(absence), workdayDates.contains(date)));
        }
        return days;
    }
}
