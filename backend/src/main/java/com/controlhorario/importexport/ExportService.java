package com.controlhorario.importexport;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.controlhorario.absence.AbsenceRepository;
import com.controlhorario.calendar.calc.PeriodCalendar;
import com.controlhorario.calendar.calc.PeriodRules;
import com.controlhorario.common.time.UserClock;
import com.controlhorario.common.web.NotFoundException;
import com.controlhorario.common.web.ValidationException;
import com.controlhorario.importexport.excel.ExcelMonthWriter;
import com.controlhorario.importexport.excel.ExcelPeriodWriter;
import com.controlhorario.period.PeriodRulesFactory;
import com.controlhorario.period.PeriodService;
import com.controlhorario.period.WorkPeriod;
import com.controlhorario.period.WorkPeriodRepository;
import com.controlhorario.summary.calc.AbsenceInput;
import com.controlhorario.summary.calc.AbsenceInputs;
import com.controlhorario.summary.calc.MonthSummary;
import com.controlhorario.summary.calc.MonthSummaryService;
import com.controlhorario.summary.calc.PeriodSummary;
import com.controlhorario.summary.calc.PeriodSummaryService;
import com.controlhorario.user.User;
import com.controlhorario.user.UserRepository;
import com.controlhorario.workday.Workday;
import com.controlhorario.workday.WorkdayRepository;
import com.controlhorario.workday.calc.WorkdayInput;
import com.controlhorario.workday.calc.WorkdayInputs;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exportación con el diseño de la app, que también se puede reimportar: un mes ({@link ExcelMonthWriter}) o
 * el periodo completo con su hoja Resumen y una hoja por mes ({@link ExcelPeriodWriter}). Los saldos son los
 * mismos que enseña la app: cada mes abre con el acumulado al cerrar el anterior.
 */
@Service
public class ExportService {

    static final int MIN_YEAR = 2000;
    static final int MAX_YEAR = 2100;

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Fichero generado: {@code horas-YYYY-MM.xlsx} o {@code horas-<periodo>.xlsx}. */
    public record ExportedFile(String fileName, byte[] content) {
    }

    /** Fichajes, notas y ausencias de todo un periodo, con sus reglas. */
    private record PeriodData(WorkPeriod period, PeriodRules rules, PeriodCalendar calendar,
            List<WorkdayInput> workdays, Map<LocalDate, String> notes, List<AbsenceInput> absences) {
    }

    private final WorkPeriodRepository periods;
    private final PeriodService periodService;
    private final PeriodRulesFactory rulesFactory;
    private final WorkdayRepository workdays;
    private final AbsenceRepository absences;
    private final UserRepository users;
    private final UserClock userClock;
    private final MonthSummaryService monthSummaryService;
    private final PeriodSummaryService periodSummaryService;
    private final ExcelMonthWriter monthWriter = new ExcelMonthWriter();
    private final ExcelPeriodWriter periodWriter = new ExcelPeriodWriter();

    public ExportService(WorkPeriodRepository periods, PeriodService periodService, PeriodRulesFactory rulesFactory,
            WorkdayRepository workdays, AbsenceRepository absences, UserRepository users, UserClock userClock,
            MonthSummaryService monthSummaryService, PeriodSummaryService periodSummaryService) {
        this.periods = periods;
        this.periodService = periodService;
        this.rulesFactory = rulesFactory;
        this.workdays = workdays;
        this.absences = absences;
        this.users = users;
        this.userClock = userClock;
        this.monthSummaryService = monthSummaryService;
        this.periodSummaryService = periodSummaryService;
    }

    /** @param periodId periodo a usar; null = el que tiene más días en el mes */
    @Transactional(readOnly = true)
    public ExportedFile exportMonth(UUID userId, int year, int month, UUID periodId) {
        if (year < MIN_YEAR || year > MAX_YEAR) {
            throw new ValidationException("year", "El año debe estar entre " + MIN_YEAR + " y " + MAX_YEAR);
        }
        if (month < 1 || month > 12) {
            throw new ValidationException("month", "El mes debe estar entre 1 y 12");
        }
        YearMonth yearMonth = YearMonth.of(year, month);
        User user = user(userId);
        WorkPeriod period = periodId == null
                ? periodFor(userId, yearMonth)
                : ownedPeriodIn(userId, periodId, yearMonth);
        PeriodData data = load(period);
        LocalDate today = userClock.today(userId);
        PeriodSummary periodSummary = periodSummaryService.summarize(data.calendar(), data.workdays(),
                data.absences(), today);
        byte[] content = monthWriter.write(monthData(user, data, periodSummary, yearMonth, today));
        return new ExportedFile(String.format("horas-%04d-%02d.xlsx", year, month), content);
    }

    /**
     * Periodo completo: hoja Resumen y una hoja por cada mes que toca el periodo (también los futuros).
     *
     * @param periodId periodo a usar; null = el seleccionado
     */
    @Transactional(readOnly = true)
    public ExportedFile exportPeriod(UUID userId, UUID periodId) {
        User user = user(userId);
        WorkPeriod period = periodService.resolve(userId, periodId);
        PeriodData data = load(period);
        LocalDate today = userClock.today(userId);
        PeriodSummary periodSummary = periodSummaryService.summarize(data.calendar(), data.workdays(),
                data.absences(), today);
        List<ExcelMonthWriter.MonthData> months = new ArrayList<>();
        YearMonth last = YearMonth.from(period.getEndDate());
        for (YearMonth m = YearMonth.from(period.getStartDate()); !m.isAfter(last); m = m.plusMonths(1)) {
            months.add(monthData(user, data, periodSummary, m, today));
        }
        byte[] content = periodWriter.write(new ExcelPeriodWriter.PeriodData(user.getName(), user.getCompany(),
                period.getName(), periodName(period), data.rules(), periodSummary, months));
        return new ExportedFile("horas-" + slug(period) + ".xlsx", content);
    }

    private ExcelMonthWriter.MonthData monthData(User user, PeriodData data, PeriodSummary periodSummary,
            YearMonth month, LocalDate today) {
        MonthSummary summary = monthSummaryService.summarize(data.calendar(), month, data.workdays(),
                data.absences(), periodSummary.openingBalanceAt(month), today);
        return new ExcelMonthWriter.MonthData(user.getName(), user.getCompany(), periodName(data.period()),
                data.rules(), summary, data.notes());
    }

    private PeriodData load(WorkPeriod period) {
        PeriodRules rules = rulesFactory.rulesFor(period);
        List<Workday> entities = workdays.findByPeriodIdAndDateBetweenOrderByDate(period.getId(),
                period.getStartDate(), period.getEndDate());
        Map<LocalDate, String> notes = new HashMap<>();
        entities.stream().filter(w -> w.getNotes() != null).forEach(w -> notes.put(w.getDate(), w.getNotes()));
        List<AbsenceInput> periodAbsences = absences.findByPeriodIdAndDateBetweenOrderByDate(period.getId(),
                period.getStartDate(), period.getEndDate()).stream().map(AbsenceInputs::from).toList();
        return new PeriodData(period, rules, new PeriodCalendar(rules),
                entities.stream().map(WorkdayInputs::from).toList(), notes, periodAbsences);
    }

    private User user(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
    }

    /** "2026-2027 (26/05/2026 – 25/05/2027)". */
    private static String periodName(WorkPeriod period) {
        return period.getName() + " (" + DATE.format(period.getStartDate()) + " – "
                + DATE.format(period.getEndDate()) + ")";
    }

    /** Nombre del periodo apto para un fichero ("2026-2027 Prueba" → "2026-2027-prueba"); vacío: sus años. */
    static String slug(WorkPeriod period) {
        String slug = Normalizer.normalize(period.getName(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return slug.isEmpty() ? period.getStartDate().getYear() + "-" + period.getEndDate().getYear() : slug;
    }

    private WorkPeriod ownedPeriodIn(UUID userId, UUID periodId, YearMonth month) {
        return periods.findByIdAndUserId(periodId, userId)
                .filter(p -> PeriodCalendar.datesOf(month).stream().anyMatch(p::contains))
                .orElseThrow(() -> new NotFoundException("El periodo no incluye ese mes"));
    }

    /** El periodo del usuario con más días dentro del mes (empate: el más reciente); 404 si ninguno lo toca. */
    private WorkPeriod periodFor(UUID userId, YearMonth month) {
        WorkPeriod best = null;
        long bestDays = 0;
        for (WorkPeriod period : periods.findByUserIdOrderByStartDateDesc(userId)) {
            long days = PeriodCalendar.datesOf(month).stream().filter(period::contains).count();
            if (days > bestDays) {
                best = period;
                bestDays = days;
            }
        }
        if (best == null) {
            throw new NotFoundException("No hay ningún periodo que incluya ese mes");
        }
        return best;
    }
}
