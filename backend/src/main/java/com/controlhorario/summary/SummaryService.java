package com.controlhorario.summary;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.controlhorario.absence.Absence;
import com.controlhorario.absence.AbsenceDto;
import com.controlhorario.absence.AbsenceMapper;
import com.controlhorario.absence.AbsenceRepository;
import com.controlhorario.calendar.calc.PeriodCalendar;
import com.controlhorario.calendar.calc.PeriodRules;
import com.controlhorario.common.time.UserClock;
import com.controlhorario.common.web.NotFoundException;
import com.controlhorario.common.web.ValidationException;
import com.controlhorario.period.PeriodRulesFactory;
import com.controlhorario.period.PeriodService;
import com.controlhorario.period.WorkPeriod;
import com.controlhorario.period.WorkPeriodRepository;
import com.controlhorario.summary.calc.AbsenceInput;
import com.controlhorario.summary.calc.AbsenceInputs;
import com.controlhorario.summary.calc.DaySummary;
import com.controlhorario.summary.calc.MonthSummary;
import com.controlhorario.summary.calc.MonthSummaryService;
import com.controlhorario.summary.calc.PeriodSummary;
import com.controlhorario.summary.calc.PeriodSummaryService;
import com.controlhorario.workday.Workday;
import com.controlhorario.workday.WorkdayDto;
import com.controlhorario.workday.WorkdayMapper;
import com.controlhorario.workday.WorkdayRepository;
import com.controlhorario.workday.calc.WorkdayInput;
import com.controlhorario.workday.calc.WorkdayInputs;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resúmenes de mes, periodo y portada. Carga los fichajes y ausencias del periodo de una vez y
 * delega todo el cálculo en {@link MonthSummaryService} y {@link PeriodSummaryService}; aquí solo
 * se elige el periodo, se obtiene el saldo de apertura y se traducen los resultados a DTOs.
 */
@Service
public class SummaryService {

    private final WorkPeriodRepository periods;
    private final PeriodService periodService;
    private final PeriodRulesFactory rulesFactory;
    private final WorkdayRepository workdays;
    private final AbsenceRepository absences;
    private final MonthSummaryService monthSummaryService;
    private final PeriodSummaryService periodSummaryService;
    private final UserClock userClock;
    private final SummaryMapper mapper;
    private final WorkdayMapper workdayMapper;
    private final AbsenceMapper absenceMapper;

    public SummaryService(WorkPeriodRepository periods, PeriodService periodService, PeriodRulesFactory rulesFactory,
            WorkdayRepository workdays, AbsenceRepository absences, MonthSummaryService monthSummaryService,
            PeriodSummaryService periodSummaryService, UserClock userClock, SummaryMapper mapper,
            WorkdayMapper workdayMapper, AbsenceMapper absenceMapper) {
        this.periods = periods;
        this.periodService = periodService;
        this.rulesFactory = rulesFactory;
        this.workdays = workdays;
        this.absences = absences;
        this.monthSummaryService = monthSummaryService;
        this.periodSummaryService = periodSummaryService;
        this.userClock = userClock;
        this.mapper = mapper;
        this.workdayMapper = workdayMapper;
        this.absenceMapper = absenceMapper;
    }

    /** Fichajes y ausencias de un periodo, cargados con una consulta cada uno. */
    private record PeriodData(
            WorkPeriod period,
            PeriodCalendar calendar,
            Map<LocalDate, Workday> workdayByDate,
            Map<LocalDate, Absence> absenceByDate,
            List<WorkdayInput> workdayInputs,
            List<AbsenceInput> absenceInputs) {

        PeriodRules rules() {
            return calendar.rules();
        }
    }

    /**
     * Resumen de un mes. Sin {@code periodId} se usa el periodo seleccionado si toca el mes y, si no,
     * el periodo con más días en el mes (si empatan, el más reciente); 404 si ningún periodo toca el mes.
     */
    @Transactional(readOnly = true)
    public MonthSummaryDto month(UUID userId, int year, int month, UUID periodId) {
        YearMonth yearMonth = yearMonth(year, month);
        WorkPeriod period;
        if (periodId != null) {
            period = periodService.requireOwned(userId, periodId);
            if (daysInMonth(period, yearMonth) == 0) {
                throw new NotFoundException("El periodo no incluye ese mes");
            }
        } else {
            period = periodService.selected(userId).filter(p -> daysInMonth(p, yearMonth) > 0)
                    .or(() -> defaultPeriod(periods.findOverlapping(userId, yearMonth.atDay(1),
                            yearMonth.atEndOfMonth()), yearMonth))
                    .orElseThrow(() -> new NotFoundException("No hay ningún periodo en ese mes"));
        }
        PeriodData data = load(period);
        LocalDate today = userClock.today(userId);
        int opening;
        if (yearMonth.isAfter(YearMonth.from(period.getStartDate()))) {
            PeriodSummary periodSummary = periodSummaryService.summarize(data.calendar(), data.workdayInputs(),
                    data.absenceInputs(), today);
            opening = periodSummary.openingBalanceAt(yearMonth);
        } else {
            opening = data.rules().openingBalanceMin();
        }
        MonthSummary summary = monthSummaryService.summarize(data.calendar(), yearMonth, data.workdayInputs(),
                data.absenceInputs(), opening, today);
        return toDto(data, summary);
    }

    /** Equivalente a la hoja Horas. */
    @Transactional(readOnly = true)
    public PeriodSummaryDto period(UUID userId, UUID periodId) {
        WorkPeriod period = periodService.requireOwned(userId, periodId);
        PeriodData data = load(period);
        PeriodSummary s = periodSummaryService.summarize(data.calendar(), data.workdayInputs(), data.absenceInputs(),
                userClock.today(userId));
        return new PeriodSummaryDto(period.getId(), period.getName(), s.startDate(), s.endDate(), s.today(),
                s.workingDays(), s.normalDays(), s.intensiveDays(), s.calendarMinutes(), s.agreementMinutes(),
                s.marginMinutes(), mapper.toDto(s.vacations()), s.hoursToRecoverMinutes(),
                s.remainingMarginMinutes(), s.workedMinutes(), s.theoreticalRemainingMinutes(),
                s.projectionMinutes(), s.openingBalanceMinutes(), s.balanceToDateMinutes(), s.bridgeMinutes(),
                mapper.toRowDtos(s.months()), workdayMapper.toIssues(s.warnings()));
    }

    /**
     * Portada con el periodo seleccionado; sin periodo, el frontend muestra el asistente. El mes que
     * se muestra es el de hoy si el periodo lo incluye; si ya terminó, su último mes; si aún no ha
     * empezado, el primero. Los saldos "hasta hoy" usan siempre la fecha real.
     */
    @Transactional(readOnly = true)
    public DashboardDto dashboard(UUID userId) {
        LocalDate today = userClock.today(userId);
        Optional<WorkPeriod> current = periodService.selected(userId);
        if (current.isEmpty()) {
            return new DashboardDto(today, null, 0, null, null, 0, 0, false, 0, null);
        }
        WorkPeriod period = current.get();
        PeriodData data = load(period);
        PeriodCalendar calendar = data.calendar();
        PeriodSummary periodSummary = periodSummaryService.summarize(calendar, data.workdayInputs(),
                data.absenceInputs(), today);
        YearMonth month = YearMonth.from(referenceDate(period, today));
        MonthSummary monthSummary = monthSummaryService.summarize(calendar, month, data.workdayInputs(),
                data.absenceInputs(), periodSummary.openingBalanceAt(month), today);

        WorkdayDto todayWorkday = monthSummary.days().stream()
                .filter(d -> d.date().equals(today) && d.workday() != null)
                .findFirst()
                .map(d -> workdayMapper.toDto(data.workdayByDate().get(today), d.result()))
                .orElse(null);
        PeriodRules rules = data.rules();
        DashboardDto.CurrentMonth currentMonth = new DashboardDto.CurrentMonth(month.toString(),
                monthSummary.theoreticalMinutes(), monthSummary.workedMinutes(), monthSummary.differenceMinutes(),
                monthSummary.theoreticalToDateMinutes(), monthSummary.workedToDateMinutes(),
                monthSummary.differenceToDateMinutes(), monthSummary.remotePct(), monthSummary.remoteDays(),
                rules.maxRemotePct(), workdayMapper.toIssues(monthSummary.warnings()));
        DashboardDto.Vacations vacations = new DashboardDto.Vacations(periodSummary.vacations().totalDays(),
                periodSummary.vacations().takenDays(), periodSummary.vacations().remainingDays(),
                periodSummary.vacations().pendingPlannedDays());
        return new DashboardDto(today,
                new DashboardDto.PeriodRef(period.getId(), period.getName(), period.getStartDate(), period.getEndDate()),
                periodSummary.balanceToDateMinutes(), currentMonth, vacations, periodSummary.hoursToRecoverMinutes(),
                periodSummary.projectionMinutes(), calendar.isWorkingDay(today), calendar.dayMinutes(today),
                todayWorkday);
    }

    /** Hoy, ajustado al periodo: su primer día si aún no ha empezado, el último si ya terminó. */
    static LocalDate referenceDate(WorkPeriod period, LocalDate today) {
        if (today.isBefore(period.getStartDate())) {
            return period.getStartDate();
        }
        return today.isAfter(period.getEndDate()) ? period.getEndDate() : today;
    }

    private PeriodData load(WorkPeriod period) {
        PeriodCalendar calendar = rulesFactory.calendarFor(period);
        List<Workday> periodWorkdays = workdays.findByPeriodIdAndDateBetweenOrderByDate(period.getId(),
                period.getStartDate(), period.getEndDate());
        List<Absence> periodAbsences = absences.findByPeriodIdAndDateBetweenOrderByDate(period.getId(),
                period.getStartDate(), period.getEndDate());
        Map<LocalDate, Workday> workdayByDate = new HashMap<>();
        periodWorkdays.forEach(w -> workdayByDate.put(w.getDate(), w));
        Map<LocalDate, Absence> absenceByDate = new HashMap<>();
        periodAbsences.forEach(a -> absenceByDate.put(a.getDate(), a));
        return new PeriodData(period, calendar, workdayByDate, absenceByDate,
                periodWorkdays.stream().map(WorkdayInputs::from).toList(),
                periodAbsences.stream().map(AbsenceInputs::from).toList());
    }

    private MonthSummaryDto toDto(PeriodData data, MonthSummary s) {
        PeriodRules rules = data.rules();
        List<DayDto> days = s.days().stream().map(d -> toDto(data, d)).toList();
        return new MonthSummaryDto(data.period().getId(), s.month().toString(), s.status(), s.workingDays(),
                s.normalDays(), s.intensiveDays(), s.calendarMinutes(), s.theoreticalMinutes(), s.vacationDays(),
                s.vacationMinutes(), s.bridgeDays(), s.bridgeMinutes(), s.workedMinutes(), s.roundedMinutes(),
                s.differenceMinutes(), s.theoreticalToDateMinutes(), s.workedToDateMinutes(), s.roundedToDateMinutes(),
                s.differenceToDateMinutes(), s.openingBalanceMinutes(), s.closingBalanceMinutes(), s.remoteMinutes(),
                s.officeMinutes(), s.remotePct(), s.remoteDays(), rules.maxRemotePct(),
                workdayMapper.toIssues(s.warnings()), mapper.toWeekDtos(s.weeks()), days);
    }

    private DayDto toDto(PeriodData data, DaySummary day) {
        WorkdayDto workday = day.workday() == null ? null
                : workdayMapper.toDto(data.workdayByDate().get(day.date()), day.result());
        AbsenceDto absence = day.absence() == null ? null
                : absenceMapper.toDto(data.absenceByDate().get(day.date()));
        return new DayDto(day.date(), day.dayType(), day.intensive(), day.dayMinutes(), day.holidayName(), absence,
                workday, day.theoreticalMinutes(), day.workedMinutes(), day.roundedMinutes(), day.countdownMinutes(),
                workdayMapper.toIssues(day.warnings()));
    }

    /** Periodo con más días dentro del mes; si empatan, el más reciente. */
    static Optional<WorkPeriod> defaultPeriod(List<WorkPeriod> candidates, YearMonth month) {
        return candidates.stream()
                .filter(p -> daysInMonth(p, month) > 0)
                .max(Comparator.comparingLong((WorkPeriod p) -> daysInMonth(p, month))
                        .thenComparing(WorkPeriod::getStartDate));
    }

    static long daysInMonth(WorkPeriod period, YearMonth month) {
        LocalDate from = period.getStartDate().isAfter(month.atDay(1)) ? period.getStartDate() : month.atDay(1);
        LocalDate to = period.getEndDate().isBefore(month.atEndOfMonth()) ? period.getEndDate() : month.atEndOfMonth();
        return to.isBefore(from) ? 0 : ChronoUnit.DAYS.between(from, to) + 1;
    }

    private static YearMonth yearMonth(int year, int month) {
        if (month < 1 || month > 12) {
            throw new ValidationException("month", "El mes debe estar entre 1 y 12");
        }
        if (year < 1900 || year > 2999) {
            throw new ValidationException("year", "Año no válido");
        }
        return YearMonth.of(year, month);
    }
}
