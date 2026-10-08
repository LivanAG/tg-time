package com.controlhorario.period;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import com.controlhorario.absence.AbsenceRepository;
import com.controlhorario.calendar.HolidayPreloader;
import com.controlhorario.calendar.HolidayRepository;
import com.controlhorario.common.audit.AuditService;
import com.controlhorario.common.time.UserClock;
import com.controlhorario.common.web.ConflictException;
import com.controlhorario.common.web.NotFoundException;
import com.controlhorario.common.web.ValidationException;
import com.controlhorario.workday.WorkdayRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Periodos anuales del usuario y sus rangos de intensiva. Todas las operaciones filtran por el
 * usuario del token: un periodo ajeno o inexistente es siempre 404.
 * <p>
 * Cada periodo tiene sus propios fichajes y ausencias, así que los periodos pueden solaparse (p. ej.
 * uno de pruebas junto al real); borrar un periodo borra también sus registros.
 * <p>
 * Periodo seleccionado (con el que trabajan todas las pantallas): el marcado por el usuario; si no
 * hay ninguno, el que contiene hoy; si tampoco, el más reciente. Crear un periodo lo selecciona.
 */
@Service
public class PeriodService {

    /** Un año completo en minutos: tope de las horas de convenio y del saldo inicial. */
    public static final int MAX_AGREEMENT_MINUTES = 366 * 1440;
    /** Duración máxima de un periodo (un periodo "anual" con holgura de sobra). */
    static final int MAX_PERIOD_YEARS = 2;

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final WorkPeriodRepository periods;
    private final IntensiveRangeRepository ranges;
    private final HolidayRepository holidays;
    private final HolidayPreloader holidayPreloader;
    private final PeriodMapper mapper;
    private final AuditService audit;
    private final UserClock userClock;
    private final WorkdayRepository workdays;
    private final AbsenceRepository absences;

    public PeriodService(WorkPeriodRepository periods, IntensiveRangeRepository ranges, HolidayRepository holidays,
            HolidayPreloader holidayPreloader, PeriodMapper mapper, AuditService audit, UserClock userClock,
            WorkdayRepository workdays, AbsenceRepository absences) {
        this.periods = periods;
        this.ranges = ranges;
        this.holidays = holidays;
        this.holidayPreloader = holidayPreloader;
        this.mapper = mapper;
        this.audit = audit;
        this.userClock = userClock;
        this.workdays = workdays;
        this.absences = absences;
    }

    /** Periodo del usuario o 404 (también si es de otro usuario). */
    @Transactional(readOnly = true)
    public WorkPeriod requireOwned(UUID userId, UUID periodId) {
        return periods.findByIdAndUserId(periodId, userId)
                .orElseThrow(() -> new NotFoundException("Periodo no encontrado"));
    }

    @Transactional(readOnly = true)
    public List<PeriodDto> list(UUID userId) {
        List<WorkPeriod> all = periods.findByUserIdOrderByStartDateDesc(userId);
        if (all.isEmpty()) {
            return List.of();
        }
        UUID selectedId = selectedOf(all, userClock.today(userId)).map(WorkPeriod::getId).orElse(null);
        Map<UUID, List<IntensiveRange>> rangesByPeriod = ranges
                .findByPeriodIdInOrderByStartDate(all.stream().map(WorkPeriod::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(IntensiveRange::getPeriodId));
        return all.stream()
                .map(p -> mapper.toDto(p, rangesByPeriod.getOrDefault(p.getId(), List.of()),
                        p.getId().equals(selectedId)))
                .toList();
    }

    /** Periodo con el que trabaja el usuario (ver la clase); vacío si no tiene ninguno. */
    @Transactional(readOnly = true)
    public Optional<WorkPeriod> selected(UUID userId) {
        return selectedOf(periods.findByUserIdOrderByStartDateDesc(userId), userClock.today(userId));
    }

    /** @param all periodos del usuario, el más reciente primero */
    static Optional<WorkPeriod> selectedOf(List<WorkPeriod> all, LocalDate today) {
        return all.stream().filter(WorkPeriod::isSelected).findFirst()
                .or(() -> all.stream().filter(p -> p.contains(today)).findFirst())
                .or(() -> all.stream().findFirst());
    }

    /**
     * Periodo en el que se lee o escribe: el indicado o, sin {@code periodId}, el seleccionado. 400 si el
     * usuario no tiene ningún periodo; 404 si el indicado no es suyo.
     */
    @Transactional(readOnly = true)
    public WorkPeriod resolve(UUID userId, UUID periodId) {
        if (periodId != null) {
            return requireOwned(userId, periodId);
        }
        return selected(userId).orElseThrow(() -> new ValidationException("periodId", "Crea primero un periodo"));
    }

    /** 400 si la fecha no está dentro del periodo. */
    public static void requireDateIn(WorkPeriod period, LocalDate date) {
        if (!period.contains(date)) {
            throw new ValidationException("date", "El " + DATE.format(date) + " no está dentro del periodo «"
                    + period.getName() + "»");
        }
    }

    /** Marca el periodo como seleccionado (y desmarca el anterior). No cambia la versión del periodo. */
    @Transactional
    public void select(UUID userId, UUID periodId) {
        requireOwned(userId, periodId);
        periods.clearSelected(userId);
        periods.markSelected(userId, periodId);
    }

    @Transactional(readOnly = true)
    public PeriodDto get(UUID userId, UUID periodId) {
        return toDto(requireOwned(userId, periodId));
    }

    @Transactional
    public PeriodDto create(UUID userId, PeriodCreateRequest request) {
        validateParameters(request);
        IntensiveRangeValidator.validate(request.intensiveRanges(), request.startDate(), request.endDate());

        WorkPeriod period = new WorkPeriod();
        period.setUserId(userId);
        apply(period, request);
        periods.saveAndFlush(period);

        if (request.intensiveRanges() != null) {
            ranges.saveAll(request.intensiveRanges().stream()
                    .map(r -> new IntensiveRange(period.getId(), r.startDate(), r.endDate()))
                    .toList());
        }
        if (!Boolean.FALSE.equals(request.preloadHolidays())) {
            holidayPreloader.preload(period.getId(), period.getStartDate(), period.getEndDate());
        }
        select(userId, period.getId());
        return mapper.toDto(period, ranges.findByPeriodIdOrderByStartDate(period.getId()), true);
    }

    @Transactional
    public PeriodDto update(UUID userId, UUID periodId, PeriodUpdateRequest request) {
        WorkPeriod period = requireOwned(userId, periodId);
        if (!Objects.equals(period.getVersion(), request.version())) {
            throw new ConflictException(
                    "El periodo ha cambiado en otra pestaña o dispositivo. Recarga y vuelve a intentarlo.");
        }
        validateParameters(request);
        ensureRecordsInside(periodId, request.startDate(), request.endDate());
        apply(period, request);
        periods.saveAndFlush(period);
        return toDto(period);
    }

    /** Borra el periodo con sus festivos y rangos de intensiva; sus fichajes y ausencias, en cascada en la BD. */
    @Transactional
    public void delete(UUID userId, UUID periodId) {
        WorkPeriod period = requireOwned(userId, periodId);
        holidays.deleteByPeriodId(periodId);
        ranges.deleteByPeriodId(periodId);
        periods.delete(period);
        periods.flush();
        audit.record(userId, "DELETE", "PERIOD", periodId.toString());
    }

    @Transactional(readOnly = true)
    public List<IntensiveRangeDto> intensiveRanges(UUID userId, UUID periodId) {
        requireOwned(userId, periodId);
        return mapper.toRangeDtos(ranges.findByPeriodIdOrderByStartDate(periodId));
    }

    /** Sustituye todos los rangos de intensiva del periodo. */
    @Transactional
    public List<IntensiveRangeDto> replaceIntensiveRanges(UUID userId, UUID periodId, List<IntensiveRangeDto> body) {
        WorkPeriod period = requireOwned(userId, periodId);
        List<IntensiveRangeDto> newRanges = body == null ? List.of() : body;
        IntensiveRangeValidator.validate(newRanges, period.getStartDate(), period.getEndDate());
        ranges.deleteByPeriodId(periodId);
        ranges.saveAll(newRanges.stream()
                .map(r -> new IntensiveRange(periodId, r.startDate(), r.endDate()))
                .toList());
        ranges.flush();
        return mapper.toRangeDtos(ranges.findByPeriodIdOrderByStartDate(periodId));
    }

    private PeriodDto toDto(WorkPeriod period) {
        boolean selected = selected(period.getUserId()).map(p -> p.getId().equals(period.getId())).orElse(false);
        return mapper.toDto(period, ranges.findByPeriodIdOrderByStartDate(period.getId()), selected);
    }

    /** Al cambiar las fechas, ningún fichaje ni ausencia del periodo puede quedar fuera. */
    private void ensureRecordsInside(UUID periodId, LocalDate start, LocalDate end) {
        if (workdays.existsByPeriodIdAndDateBefore(periodId, start)
                || absences.existsByPeriodIdAndDateBefore(periodId, start)) {
            throw new ValidationException("startDate", "Hay fichajes o ausencias antes del " + DATE.format(start)
                    + ": bórralos antes de acortar el periodo");
        }
        if (workdays.existsByPeriodIdAndDateAfter(periodId, end)
                || absences.existsByPeriodIdAndDateAfter(periodId, end)) {
            throw new ValidationException("endDate", "Hay fichajes o ausencias después del " + DATE.format(end)
                    + ": bórralos antes de acortar el periodo");
        }
    }

    /** Reglas que no cubre Bean Validation (las de rango y obligatoriedad ya vienen validadas). */
    static void validateParameters(PeriodParameters p) {
        if (!p.startDate().isBefore(p.endDate())) {
            throw new ValidationException("endDate", "La fecha de fin debe ser posterior a la de inicio");
        }
        if (p.endDate().isAfter(p.startDate().plusYears(MAX_PERIOD_YEARS))) {
            throw new ValidationException("endDate", "Un periodo no puede durar más de " + MAX_PERIOD_YEARS + " años");
        }
    }

    private static void apply(WorkPeriod period, PeriodParameters p) {
        period.setName(p.name().strip());
        period.setStartDate(p.startDate());
        period.setEndDate(p.endDate());
        period.setAgreementMinutes(p.agreementMinutes());
        period.setVacationDays(p.vacationDays());
        period.setNormalDayMinutes(p.normalDayMinutes());
        period.setIntensiveDayMinutes(p.intensiveDayMinutes());
        period.setBreakfastToleranceMin(p.breakfastToleranceMin());
        period.setMinLunchMin(p.minLunchMin());
        period.setRoundingStepMin(p.roundingStepMin());
        period.setMaxRemotePct(p.maxRemotePct());
        period.setOpeningBalanceMin(p.openingBalanceMin() == null ? 0 : p.openingBalanceMin());
    }
}
