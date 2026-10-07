package com.controlhorario.importexport;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import com.controlhorario.absence.AbsenceRepository;
import com.controlhorario.calendar.calc.PeriodCalendar;
import com.controlhorario.calendar.calc.PeriodRules;
import com.controlhorario.common.time.UserClock;
import com.controlhorario.common.web.NotFoundException;
import com.controlhorario.common.web.ValidationException;
import com.controlhorario.importexport.excel.ExcelMonthWriter;
import com.controlhorario.period.PeriodRulesFactory;
import com.controlhorario.period.WorkPeriod;
import com.controlhorario.period.WorkPeriodRepository;
import com.controlhorario.summary.calc.AbsenceInput;
import com.controlhorario.summary.calc.AbsenceInputs;
import com.controlhorario.summary.calc.MonthSummary;
import com.controlhorario.summary.calc.MonthSummaryService;
import com.controlhorario.user.User;
import com.controlhorario.user.UserRepository;
import com.controlhorario.workday.WorkdayRepository;
import com.controlhorario.workday.calc.WorkdayInput;
import com.controlhorario.workday.calc.WorkdayInputs;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Exportación de un mes con el formato de la hoja mensual del Excel original. */
@Service
public class ExportService {

    static final int MIN_YEAR = 2000;
    static final int MAX_YEAR = 2100;

    /** Fichero generado: {@code horas-YYYY-MM.xlsx}. */
    public record ExportedFile(String fileName, byte[] content) {
    }

    private final WorkPeriodRepository periods;
    private final PeriodRulesFactory rulesFactory;
    private final WorkdayRepository workdays;
    private final AbsenceRepository absences;
    private final UserRepository users;
    private final UserClock userClock;
    private final MonthSummaryService monthSummaryService;
    private final ExcelMonthWriter writer = new ExcelMonthWriter();

    public ExportService(WorkPeriodRepository periods, PeriodRulesFactory rulesFactory, WorkdayRepository workdays,
            AbsenceRepository absences, UserRepository users, UserClock userClock,
            MonthSummaryService monthSummaryService) {
        this.periods = periods;
        this.rulesFactory = rulesFactory;
        this.workdays = workdays;
        this.absences = absences;
        this.users = users;
        this.userClock = userClock;
        this.monthSummaryService = monthSummaryService;
    }

    @Transactional(readOnly = true)
    public ExportedFile exportMonth(UUID userId, int year, int month) {
        if (year < MIN_YEAR || year > MAX_YEAR) {
            throw new ValidationException("year", "El año debe estar entre " + MIN_YEAR + " y " + MAX_YEAR);
        }
        if (month < 1 || month > 12) {
            throw new ValidationException("month", "El mes debe estar entre 1 y 12");
        }
        YearMonth yearMonth = YearMonth.of(year, month);
        User user = users.findById(userId).orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
        WorkPeriod period = periodFor(userId, yearMonth);
        PeriodRules rules = rulesFactory.rulesFor(period);

        LocalDate from = yearMonth.atDay(1);
        LocalDate to = yearMonth.atEndOfMonth();
        List<WorkdayInput> monthWorkdays = workdays.findByUserIdAndDateBetweenOrderByDate(userId, from, to).stream()
                .map(WorkdayInputs::from)
                .toList();
        List<AbsenceInput> monthAbsences = absences.findByUserIdAndDateBetweenOrderByDate(userId, from, to).stream()
                .map(AbsenceInputs::from)
                .toList();
        MonthSummary summary = monthSummaryService.summarize(new PeriodCalendar(rules), yearMonth, monthWorkdays,
                monthAbsences, rules.openingBalanceMin(), userClock.today(userId));

        byte[] content = writer.write(new ExcelMonthWriter.MonthData(user.getName(), user.getCompany(), rules, summary,
                monthWorkdays));
        return new ExportedFile(String.format("horas-%04d-%02d.xlsx", year, month), content);
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
