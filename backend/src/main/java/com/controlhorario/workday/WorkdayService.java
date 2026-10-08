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
import com.controlhorario.period.PeriodService;
import com.controlhorario.period.WorkPeriod;
import com.controlhorario.workday.calc.BreakInput;
import com.controlhorario.workday.calc.MixedTimes;
import com.controlhorario.workday.calc.WorkdayCalculator;
import com.controlhorario.workday.calc.WorkdayInput;
import com.controlhorario.workday.calc.WorkdayInputs;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registro diario. Cada periodo tiene sus propios fichajes: todas las operaciones van sobre el periodo
 * indicado ({@code periodId}) o, sin él, sobre el seleccionado. El PUT crea o actualiza el día
 * (idempotente) con bloqueo optimista; los totales se calculan con las reglas de ese periodo.
 */
@Service
public class WorkdayService {

    static final int MAX_RANGE_DAYS = 400;

    static final String VERSION_CHANGED =
            "El registro ha cambiado en otra pestaña o dispositivo. Recarga y vuelve a intentarlo.";

    private final WorkdayRepository workdays;
    private final PeriodService periods;
    private final PeriodRulesFactory rulesFactory;
    private final WorkdayMapper mapper;
    private final AuditService audit;

    public WorkdayService(WorkdayRepository workdays, PeriodService periods, PeriodRulesFactory rulesFactory,
            WorkdayMapper mapper, AuditService audit) {
        this.workdays = workdays;
        this.periods = periods;
        this.rulesFactory = rulesFactory;
        this.mapper = mapper;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<WorkdayDto> list(UUID userId, UUID periodId, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new ValidationException("to", "La fecha final debe ser igual o posterior a la inicial");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_RANGE_DAYS) {
            throw new ValidationException("to", "El rango no puede superar " + MAX_RANGE_DAYS + " días");
        }
        WorkPeriod period = periods.resolve(userId, periodId);
        WorkdayCalculator calculator = calculatorFor(period);
        return workdays.findByPeriodIdAndDateBetweenOrderByDate(period.getId(), from, to).stream()
                .map(w -> toDto(w, calculator))
                .toList();
    }

    /** Fichaje del día en el periodo, con sus totales. 404 si no hay. */
    @Transactional(readOnly = true)
    public WorkdayDto get(UUID userId, UUID periodId, LocalDate date) {
        WorkPeriod period = periods.resolve(userId, periodId);
        return workdays.findByPeriodIdAndDate(period.getId(), date)
                .map(w -> toDto(w, calculatorFor(period)))
                .orElseThrow(() -> new NotFoundException("No hay registro ese día"));
    }

    /**
     * Crea (version null) o actualiza (version del último GET) el fichaje del día. 409 si la versión
     * no coincide o si se intenta crear un día que ya existe.
     */
    @Transactional
    public WorkdayDto put(UUID userId, UUID periodId, LocalDate date, WorkdayRequest request) {
        WorkPeriod period = periods.resolve(userId, periodId);
        PeriodService.requireDateIn(period, date);
        PeriodRules rules = rulesFactory.rulesFor(period);
        WorkdayCalculator calculator = new WorkdayCalculator(rules.breakfastToleranceMin(), rules.minLunchMin());

        Location location = request.location() == null ? Location.OFICINA : request.location();
        MixedTimes mixed = new MixedTimes(request.officeStart(), request.officeEnd(), request.homeStart(),
                request.homeEnd());
        List<BreakInput> breaks = request.breaks() == null ? List.of() : request.breaks().stream()
                .map(b -> new BreakInput(b.type(), b.startTime(), b.endTime()))
                .toList();
        // En MIXTO, la entrada y la salida del día salen de los tramos.
        WorkdayInput input = new WorkdayInput(date, request.startTime(), request.endTime(), breaks, location, mixed);
        List<CalcIssue> errors = calculator.validate(input);
        if (!errors.isEmpty()) {
            throw new ValidationException(errors.stream()
                    .map(e -> new FieldErrorDto(e.field(), e.message()))
                    .toList());
        }

        Optional<Workday> existing = workdays.findByPeriodIdAndDate(period.getId(), date);
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
            workday = new Workday(userId, period.getId(), date);
        }

        boolean breaksChanged = !sameBreaks(workday.getBreaks(), breaks);
        workday.setStartTime(input.start());
        workday.setEndTime(input.end());
        workday.setLocation(location);
        workday.setMixed(input.mixed());
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
    public void delete(UUID userId, UUID periodId, LocalDate date) {
        WorkPeriod period = periods.resolve(userId, periodId);
        Workday workday = workdays.findByPeriodIdAndDate(period.getId(), date)
                .orElseThrow(() -> new NotFoundException("No hay registro ese día"));
        workdays.delete(workday);
        workdays.flush();
        audit.record(userId, "DELETE", "WORKDAY", workday.getId().toString());
    }

    private WorkdayDto toDto(Workday workday, WorkdayCalculator calculator) {
        return mapper.toDto(workday, calculator.calculate(WorkdayInputs.from(workday)));
    }

    private static WorkdayCalculator calculatorFor(WorkPeriod period) {
        return new WorkdayCalculator(period.getBreakfastToleranceMin(), period.getMinLunchMin());
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
