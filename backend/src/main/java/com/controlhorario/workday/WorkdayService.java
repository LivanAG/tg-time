package com.controlhorario.workday;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;


import com.controlhorario.calendar.calc.PeriodRules;
import com.controlhorario.common.audit.AuditService;
import com.controlhorario.common.calc.CalcIssue;
import com.controlhorario.common.web.ConflictException;
import com.controlhorario.common.web.FieldErrorDto;
import com.controlhorario.common.web.NotFoundException;
import com.controlhorario.common.web.ValidationException;
import com.controlhorario.period.PeriodRulesFactory;
import com.controlhorario.period.WorkPeriod;
import com.controlhorario.period.WorkPeriodRepository;
import com.controlhorario.workday.calc.BreakInput;
import com.controlhorario.workday.calc.WorkdayCalculator;
import com.controlhorario.workday.calc.WorkdayInput;
import com.controlhorario.workday.calc.WorkdayInputs;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registro diario. El PUT crea o actualiza el día (idempotente) con bloqueo optimista; los
 * totales se calculan siempre con las reglas del periodo que contiene la fecha.
 */
@Service
public class WorkdayService {

    /** Reglas para un fichaje que ya no tiene periodo (p. ej. si se borró): las del Excel. */
    static final int DEFAULT_BREAKFAST_TOLERANCE_MIN = 20;
    static final int DEFAULT_MIN_LUNCH_MIN = 30;
    static final int MAX_RANGE_DAYS = 400;

    static final String VERSION_CHANGED =
            "El registro ha cambiado en otra pestaña o dispositivo. Recarga y vuelve a intentarlo.";

    private final WorkdayRepository workdays;
    private final WorkPeriodRepository periods;
    private final PeriodRulesFactory rulesFactory;
    private final WorkdayMapper mapper;
    private final AuditService audit;

    public WorkdayService(WorkdayRepository workdays, WorkPeriodRepository periods, PeriodRulesFactory rulesFactory,
            WorkdayMapper mapper, AuditService audit) {
        this.workdays = workdays;
        this.periods = periods;
        this.rulesFactory = rulesFactory;
        this.mapper = mapper;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<WorkdayDto> list(UUID userId, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new ValidationException("to", "La fecha final debe ser igual o posterior a la inicial");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_RANGE_DAYS) {
            throw new ValidationException("to", "El rango no puede superar " + MAX_RANGE_DAYS + " días");
        }
        List<WorkPeriod> overlapping = periods.findOverlapping(userId, from, to);
        return workdays.findByUserIdAndDateBetweenOrderByDate(userId, from, to).stream()
                .map(w -> toDto(w, calculatorFor(w.getDate(), overlapping)))
                .toList();
    }

    @Transactional(readOnly = true)
    public WorkdayDto get(UUID userId, LocalDate date) {
        return find(userId, date).orElseThrow(() -> new NotFoundException("No hay registro ese día"));
    }

    /** Fichaje del día con sus totales, si existe. */
    @Transactional(readOnly = true)
    public Optional<WorkdayDto> find(UUID userId, LocalDate date) {
        return workdays.findByUserIdAndDate(userId, date)
                .map(w -> toDto(w, calculatorFor(date, periods.findContaining(userId, date).stream().toList())));
    }

    /**
     * Crea (version null) o actualiza (version del último GET) el fichaje del día. 409 si la versión
     * no coincide o si se intenta crear un día que ya existe.
     */
    @Transactional
    public WorkdayDto put(UUID userId, LocalDate date, WorkdayRequest request) {
        WorkPeriod period = periods.findContaining(userId, date)
                .orElseThrow(() -> new ValidationException("date", "No hay ningún periodo que incluya esta fecha"));
        PeriodRules rules = rulesFactory.rulesFor(period);
        WorkdayCalculator calculator = new WorkdayCalculator(rules.breakfastToleranceMin(), rules.minLunchMin());

        Location location = request.location() == null ? Location.OFICINA : request.location();
        Integer remoteMinutes = location == Location.MIXTO ? request.remoteMinutes() : null;
        List<BreakInput> breaks = request.breaks() == null ? List.of() : request.breaks().stream()
                .map(b -> new BreakInput(b.type(), b.startTime(), b.endTime()))
                .toList();
        WorkdayInput input = new WorkdayInput(date, request.startTime(), request.endTime(), breaks, location,
                remoteMinutes, request.jiraMinutes(), request.izertiaMinutes());
        List<CalcIssue> errors = calculator.validate(input);
        if (!errors.isEmpty()) {
            throw new ValidationException(errors.stream()
                    .map(e -> new FieldErrorDto(e.field(), e.message()))
                    .toList());
        }

        Optional<Workday> existing = workdays.findByUserIdAndDate(userId, date);
        Workday workday;
        if (existing.isPresent()) {
            workday = existing.get();
            if (request.version() == null) {
                throw new ConflictException("Ya hay un registro para este día. Recarga y vuelve a intentarlo.");
            }
            if (!request.version().equals(workday.getVersion())) {
                throw new ConflictException(VERSION_CHANGED);
            }
        } else {
            if (request.version() != null) {
                throw new ConflictException("El registro de este día ya no existe. Recarga y vuelve a intentarlo.");
            }
            workday = new Workday(userId, date);
        }

        boolean breaksChanged = !sameBreaks(workday.getBreaks(), breaks);
        workday.setStartTime(request.startTime());
        workday.setEndTime(request.endTime());
        workday.setLocation(location);
        workday.setRemoteMinutes(remoteMinutes);
        workday.setJiraMinutes(request.jiraMinutes());
        workday.setIzertiaMinutes(request.izertiaMinutes());
        workday.setNotes(blankToNull(request.notes()));
        if (breaksChanged) {
            workday.replaceBreaks(breaks.stream()
                    .map(b -> new WorkdayBreak(b.type(), b.start(), b.end()))
                    .toList());
        }
        workdays.saveAndFlush(workday);
        return toDto(workday, calculator);
    }

    @Transactional
    public void delete(UUID userId, LocalDate date) {
        Workday workday = require(userId, date);
        workdays.delete(workday);
        workdays.flush();
        audit.record(userId, "DELETE", "WORKDAY", workday.getId().toString());
    }

    private Workday require(UUID userId, LocalDate date) {
        return workdays.findByUserIdAndDate(userId, date)
                .orElseThrow(() -> new NotFoundException("No hay registro ese día"));
    }

    private WorkdayDto toDto(Workday workday, WorkdayCalculator calculator) {
        return mapper.toDto(workday, calculator.calculate(WorkdayInputs.from(workday)));
    }

    /** Calculadora con las reglas del periodo que contiene la fecha, o las del Excel si no hay. */
    static WorkdayCalculator calculatorFor(LocalDate date, List<WorkPeriod> candidates) {
        return candidates.stream()
                .filter(p -> p.contains(date))
                .findFirst()
                .map(p -> new WorkdayCalculator(p.getBreakfastToleranceMin(), p.getMinLunchMin()))
                .orElseGet(() -> new WorkdayCalculator(DEFAULT_BREAKFAST_TOLERANCE_MIN, DEFAULT_MIN_LUNCH_MIN));
    }

    private static boolean sameBreaks(List<WorkdayBreak> current, List<BreakInput> next) {
        if (current.size() != next.size()) {
            return false;
        }
        List<WorkdayBreak> a = current.stream().sorted(Comparator.comparing(WorkdayBreak::getStartTime)).toList();
        List<BreakInput> b = next.stream().sorted(Comparator.comparing(BreakInput::start)).toList();
        for (int i = 0; i < a.size(); i++) {
            if (a.get(i).getType() != b.get(i).type()
                    || !a.get(i).getStartTime().equals(b.get(i).start())
                    || !a.get(i).getEndTime().equals(b.get(i).end())) {
                return false;
            }
        }
        return true;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
