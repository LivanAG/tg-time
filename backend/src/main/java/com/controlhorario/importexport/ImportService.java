package com.controlhorario.importexport;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.controlhorario.absence.Absence;
import com.controlhorario.absence.AbsenceRepository;
import com.controlhorario.absence.AbsenceType;
import com.controlhorario.calendar.calc.PeriodCalendar;
import com.controlhorario.calendar.calc.PeriodRules;
import com.controlhorario.common.audit.AuditService;
import com.controlhorario.common.calc.Minutes;
import com.controlhorario.common.time.UserClock;
import com.controlhorario.common.web.NotFoundException;
import com.controlhorario.common.web.ValidationException;
import com.controlhorario.importexport.dto.ImportAbsenceDto;
import com.controlhorario.importexport.dto.ImportAction;
import com.controlhorario.importexport.dto.ImportCountsDto;
import com.controlhorario.importexport.dto.ImportDayDto;
import com.controlhorario.importexport.dto.ImportDayStatus;
import com.controlhorario.importexport.dto.ImportResultDto;
import com.controlhorario.importexport.dto.ImportWorkdayDto;
import com.controlhorario.importexport.excel.ExcelFileException;
import com.controlhorario.importexport.excel.ExcelWorkbookParser;
import com.controlhorario.importexport.excel.ExcelWorkbookReader;
import com.controlhorario.importexport.excel.ParsedDay;
import com.controlhorario.importexport.excel.ParsedWorkbook;
import com.controlhorario.period.PeriodRulesFactory;
import com.controlhorario.period.WorkPeriod;
import com.controlhorario.period.WorkPeriodRepository;
import com.controlhorario.workday.Location;
import com.controlhorario.workday.Workday;
import com.controlhorario.workday.WorkdayBreak;
import com.controlhorario.workday.WorkdayRepository;
import com.controlhorario.workday.calc.WorkdayCalculator;
import com.controlhorario.workday.calc.WorkdayInput;
import com.controlhorario.workday.calc.WorkdayResult;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Importación del Excel HORAS_IZERTIS (docs/EXCEL.md y docs/API.md).
 * <p>
 * Siempre se evalúa el fichero entero: estado y acción de cada día con fichaje, cálculo con las
 * reglas del periodo comparado con el Total Día del Excel y vacaciones deducidas. Con
 * {@code dryRun=false} la misma evaluación se repite y se guarda en una única transacción.
 */
@Service
public class ImportService {

    static final String NO_PERIOD = "Crea primero el periodo del Excel en Ajustes";
    static final String NO_SHEETS = "No se ha encontrado ninguna hoja mensual (con fechas en la columna A)";
    static final String AUDIT_ACTION = "IMPORT";
    static final String AUDIT_ENTITY = "xlsx";

    private final WorkPeriodRepository periods;
    private final PeriodRulesFactory rulesFactory;
    private final WorkdayRepository workdays;
    private final AbsenceRepository absences;
    private final AuditService audit;
    private final UserClock userClock;
    private final ImportMapper mapper;
    private final TransactionTemplate readTransaction;
    private final TransactionTemplate writeTransaction;
    private final ExcelWorkbookParser parser = new ExcelWorkbookParser();

    /** Evaluación de un día con fichaje. {@code existing} es el registro que ya había ese día, si lo hay. */
    private record DayPlan(ParsedDay day, WorkdayInput input, ImportDayStatus status, ImportAction action,
            Integer computedWorkedMinutes, boolean mismatch, List<String> messages, Workday existing) {
    }

    private record AbsencePlan(LocalDate date, ImportAction action, String reason) {
    }

    private record Plan(WorkPeriod period, ParsedWorkbook parsed, ImportOptions options, List<DayPlan> days,
            List<AbsencePlan> absences, List<String> warnings) {
    }

    public ImportService(WorkPeriodRepository periods, PeriodRulesFactory rulesFactory, WorkdayRepository workdays,
            AbsenceRepository absences, AuditService audit, UserClock userClock, ImportMapper mapper,
            PlatformTransactionManager transactionManager) {
        this.periods = periods;
        this.rulesFactory = rulesFactory;
        this.workdays = workdays;
        this.absences = absences;
        this.audit = audit;
        this.userClock = userClock;
        this.mapper = mapper;
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setReadOnly(true);
        this.writeTransaction = new TransactionTemplate(transactionManager);
    }

    /**
     * Evalúa el Excel y, si {@code dryRun} es false, guarda los días y las vacaciones con acción IMPORT.
     *
     * @param content contenido ya validado (tamaño, tipo y firma) por {@link XlsxUploadValidator}
     */
    public ImportResultDto importXlsx(UUID userId, String fileName, byte[] content, ImportOptions options) {
        ParsedWorkbook parsed;
        try {
            parsed = ExcelWorkbookReader.read(content, parser::parse);
        } catch (ExcelFileException e) {
            throw new ValidationException("file", e.getMessage());
        }
        if (parsed.sheets().isEmpty()) {
            throw new ValidationException("file", NO_SHEETS);
        }
        LocalDate today = userClock.today(userId);
        if (options.dryRun()) {
            return Objects.requireNonNull(readTransaction.execute(
                    status -> toDto(plan(userId, parsed, options, today), fileName, 0, 0)));
        }
        ImportResultDto result = Objects.requireNonNull(writeTransaction.execute(status -> {
            Plan plan = plan(userId, parsed, options, today);
            int imported = saveWorkdays(userId, plan);
            int created = saveAbsences(userId, plan);
            workdays.flush();
            return toDto(plan, fileName, imported, created);
        }));
        audit.record(userId, AUDIT_ACTION, AUDIT_ENTITY, result.periodId().toString());
        return result;
    }

    // ------------------------------------------------------------------ evaluación

    private Plan plan(UUID userId, ParsedWorkbook parsed, ImportOptions options, LocalDate today) {
        WorkPeriod period = resolvePeriod(userId, options.periodId(), parsed);
        PeriodRules rules = rulesFactory.rulesFor(period);
        PeriodCalendar calendar = new PeriodCalendar(rules);
        WorkdayCalculator calculator = new WorkdayCalculator(rules.breakfastToleranceMin(), rules.minLunchMin());

        List<YearMonth> months = parsed.months();
        LocalDate from = months.get(0).atDay(1);
        LocalDate to = months.get(months.size() - 1).atEndOfMonth();
        Map<LocalDate, Workday> existingWorkdays = new HashMap<>();
        for (Workday w : workdays.findByUserIdAndDateBetweenOrderByDate(userId, from, to)) {
            existingWorkdays.put(w.getDate(), w);
        }
        Map<LocalDate, Absence> existingAbsences = new HashMap<>();
        for (Absence a : absences.findByUserIdAndDateBetweenOrderByDate(userId, from, to)) {
            existingAbsences.put(a.getDate(), a);
        }

        List<DayPlan> days = new ArrayList<>();
        Map<LocalDate, ParsedDay> seen = new HashMap<>();
        for (ParsedDay day : parsed.days()) {
            ParsedDay duplicateOf = seen.putIfAbsent(day.date(), day);
            days.add(planDay(day, duplicateOf, period, calculator, options, today, existingWorkdays.get(day.date()),
                    existingAbsences.get(day.date())));
        }

        List<AbsencePlan> vacations = planVacations(parsed, calendar, options, today, seen.keySet(),
                existingWorkdays, existingAbsences);
        return new Plan(period, parsed, options, List.copyOf(days), vacations, warnings(parsed, period, days));
    }

    /** Periodo elegido o, por defecto, el del usuario que contiene más fechas del fichero (empate: el más reciente). */
    private WorkPeriod resolvePeriod(UUID userId, UUID periodId, ParsedWorkbook parsed) {
        if (periodId != null) {
            return periods.findByIdAndUserId(periodId, userId)
                    .orElseThrow(() -> new NotFoundException("Periodo no encontrado"));
        }
        List<LocalDate> dates = parsed.days().stream().map(ParsedDay::date).toList();
        if (dates.isEmpty()) {
            dates = parsed.monthDates();
        }
        WorkPeriod best = null;
        long bestCount = 0;
        for (WorkPeriod period : periods.findByUserIdOrderByStartDateDesc(userId)) {
            long count = dates.stream().filter(period::contains).count();
            if (count > bestCount) {
                best = period;
                bestCount = count;
            }
        }
        if (best == null) {
            throw new ValidationException("periodId", NO_PERIOD);
        }
        return best;
    }

    private DayPlan planDay(ParsedDay day, ParsedDay duplicateOf, WorkPeriod period, WorkdayCalculator calculator,
            ImportOptions options, LocalDate today, Workday existing, Absence existingAbsence) {
        List<String> errors = new ArrayList<>(day.errors());
        List<String> notes = new ArrayList<>(day.messages());
        if (duplicateOf != null) {
            errors.add("Fecha repetida: ya aparece en la hoja «" + duplicateOf.sheet() + "», fila " + duplicateOf.row());
        }
        WorkdayInput input = new WorkdayInput(day.date(), day.startTime(), day.endTime(), day.breaks(),
                day.location(), day.location() == Location.MIXTO ? day.remoteMinutes() : null);

        Integer computed = null;
        if (day.representable()) {
            calculator.validate(input).forEach(issue -> errors.add(issue.message()));
            if (errors.isEmpty()) {
                WorkdayResult result = calculator.calculate(input);
                computed = result.workedMinutes();
                result.warnings().forEach(w -> notes.add(w.message()));
            }
        }
        boolean mismatch = computed != null && day.excelWorkedMinutes() != null
                && !computed.equals(day.excelWorkedMinutes());
        if (mismatch) {
            notes.add("El total calculado (" + Minutes.format(computed) + ") no coincide con el Total Día del Excel ("
                    + Minutes.format(day.excelWorkedMinutes()) + ")");
        }

        ImportDayStatus status;
        ImportAction action = ImportAction.SKIP;
        if (!period.contains(day.date())) {
            status = ImportDayStatus.OUT_OF_PERIOD;
            notes.add("Fuera del periodo «" + period.getName() + "»");
        } else if (!day.representable()) {
            status = ImportDayStatus.NOT_REPRESENTABLE;
        } else if (!errors.isEmpty()) {
            status = ImportDayStatus.INVALID;
        } else if (day.date().isAfter(today)) {
            status = ImportDayStatus.FUTURE;
            if (options.includeFuture() && (existing == null || options.overwrite())) {
                action = ImportAction.IMPORT;
            }
            if (existing != null) {
                notes.add(action == ImportAction.IMPORT ? "Ya hay un registro ese día: se sustituye"
                        : "Ya hay un registro ese día");
            }
        } else if (existing != null) {
            status = ImportDayStatus.EXISTS;
            if (options.overwrite()) {
                action = ImportAction.IMPORT;
                notes.add("Ya hay un registro ese día: se sustituye");
            }
        } else {
            status = ImportDayStatus.NEW;
            action = ImportAction.IMPORT;
        }
        if (action == ImportAction.IMPORT && existingAbsence != null) {
            notes.add("Ese día ya tiene una ausencia (" + existingAbsence.getType() + ")");
        }
        List<String> messages = new ArrayList<>(errors);
        messages.addAll(notes);
        return new DayPlan(day, input, status, action, computed, mismatch, List.copyOf(messages), existing);
    }

    /**
     * Vacaciones deducidas: días laborables del periodo (según sus festivos), hasta hoy, de los meses que
     * cubre el fichero, sin fichaje en el Excel ni en la base de datos. Si ya hay una ausencia ese día
     * no se toca.
     */
    private static List<AbsencePlan> planVacations(ParsedWorkbook parsed, PeriodCalendar calendar,
            ImportOptions options, LocalDate today, Set<LocalDate> excelDays, Map<LocalDate, Workday> existingWorkdays,
            Map<LocalDate, Absence> existingAbsences) {
        List<AbsencePlan> plans = new ArrayList<>();
        for (LocalDate date : parsed.monthDates()) {
            if (!calendar.isWorkingDay(date) || date.isAfter(today) || excelDays.contains(date)
                    || existingWorkdays.containsKey(date)) {
                continue;
            }
            Absence existing = existingAbsences.get(date);
            if (existing != null) {
                plans.add(new AbsencePlan(date, ImportAction.SKIP,
                        "Ya hay una ausencia ese día (" + existing.getType() + ")"));
            } else if (options.markVacations()) {
                plans.add(new AbsencePlan(date, ImportAction.IMPORT,
                        "Día laborable sin fichaje en el Excel: se marca como vacaciones"));
            } else {
                plans.add(new AbsencePlan(date, ImportAction.SKIP,
                        "Día laborable sin fichaje en el Excel (no se marcan vacaciones)"));
            }
        }
        return List.copyOf(plans);
    }

    private static List<String> warnings(ParsedWorkbook parsed, WorkPeriod period, List<DayPlan> days) {
        List<String> warnings = new ArrayList<>(parsed.warnings());
        long outOfPeriod = days.stream().filter(d -> d.status() == ImportDayStatus.OUT_OF_PERIOD).count();
        if (outOfPeriod > 0) {
            warnings.add(outOfPeriod + (outOfPeriod == 1 ? " día con fichaje queda" : " días con fichaje quedan")
                    + " fuera del periodo «" + period.getName() + "»");
        }
        long mismatches = days.stream().filter(DayPlan::mismatch).count();
        if (mismatches > 0) {
            warnings.add(mismatches + (mismatches == 1 ? " día no coincide" : " días no coinciden")
                    + " con el Total Día (columna L) del Excel");
        }
        return List.copyOf(warnings);
    }

    // ------------------------------------------------------------------ guardado

    private int saveWorkdays(UUID userId, Plan plan) {
        int saved = 0;
        for (DayPlan p : plan.days()) {
            if (p.action() != ImportAction.IMPORT) {
                continue;
            }
            WorkdayInput input = p.input();
            Workday workday = p.existing() != null ? p.existing() : new Workday(userId, input.date());
            workday.setStartTime(input.start());
            workday.setEndTime(input.end());
            workday.setLocation(input.location());
            workday.setRemoteMinutes(input.location() == Location.MIXTO ? input.remoteMinutes() : null);
            workday.replaceBreaks(input.breaks().stream()
                    .map(b -> new WorkdayBreak(b.type(), b.start(), b.end()))
                    .toList());
            workdays.save(workday);
            saved++;
        }
        return saved;
    }

    private int saveAbsences(UUID userId, Plan plan) {
        int saved = 0;
        for (AbsencePlan p : plan.absences()) {
            if (p.action() != ImportAction.IMPORT) {
                continue;
            }
            Absence absence = new Absence(userId, p.date());
            absence.setType(AbsenceType.VACACIONES);
            absence.setHalfDay(false);
            absences.save(absence);
            saved++;
        }
        return saved;
    }

    // ------------------------------------------------------------------ respuesta

    private ImportResultDto toDto(Plan plan, String fileName, int imported, int vacationsCreated) {
        int toImport = 0, skippedFuture = 0, skippedExisting = 0, skippedOutOfPeriod = 0, invalid = 0, mismatches = 0;
        List<ImportDayDto> days = new ArrayList<>(plan.days().size());
        for (DayPlan p : plan.days()) {
            if (p.mismatch()) {
                mismatches++;
            }
            if (p.action() == ImportAction.IMPORT) {
                toImport++;
            } else {
                switch (p.status()) {
                    case OUT_OF_PERIOD -> skippedOutOfPeriod++;
                    case INVALID, NOT_REPRESENTABLE -> invalid++;
                    case FUTURE -> {
                        if (p.existing() != null && plan.options().includeFuture()) {
                            skippedExisting++;
                        } else {
                            skippedFuture++;
                        }
                    }
                    case EXISTS -> skippedExisting++;
                    case NEW -> {
                        // Un día NEW siempre se importa.
                    }
                }
            }
            days.add(new ImportDayDto(p.day().date(), p.day().sheet(), p.day().row(), p.status(), p.action(),
                    workdayDto(p.input()), p.day().excelWorkedMinutes(), p.computedWorkedMinutes(), p.messages()));
        }
        List<ImportAbsenceDto> absenceDtos = plan.absences().stream()
                .map(a -> new ImportAbsenceDto(a.date(), AbsenceType.VACACIONES, a.action(), a.reason()))
                .toList();
        int vacationsToCreate = (int) plan.absences().stream().filter(a -> a.action() == ImportAction.IMPORT).count();
        ImportCountsDto counts = new ImportCountsDto(toImport, imported, skippedFuture, skippedExisting,
                skippedOutOfPeriod, invalid, mismatches, vacationsToCreate, vacationsCreated);
        return new ImportResultDto(plan.options().dryRun(), fileName, plan.period().getId(),
                plan.parsed().sheets().stream().map(mapper::toDto).toList(), List.copyOf(days), absenceDtos,
                mapper.toDto(plan.parsed().settings()), plan.warnings(), counts);
    }

    private ImportWorkdayDto workdayDto(WorkdayInput input) {
        return new ImportWorkdayDto(input.start(), input.end(),
                input.breaks().stream().map(mapper::toDto).toList(), input.location(),
                input.remoteMinutes(), null);
    }
}
