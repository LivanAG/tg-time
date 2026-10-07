package com.controlhorario.period;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import com.controlhorario.calendar.HolidayPreloader;
import com.controlhorario.calendar.HolidayRepository;
import com.controlhorario.common.audit.AuditService;
import com.controlhorario.common.web.ConflictException;
import com.controlhorario.common.web.NotFoundException;
import com.controlhorario.common.web.ValidationException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Periodos anuales del usuario y sus rangos de intensiva. Todas las operaciones filtran por el
 * usuario del token: un periodo ajeno o inexistente es siempre 404.
 */
@Service
public class PeriodService {

    /** Un año completo en minutos: tope de las horas de convenio y del saldo inicial. */
    public static final int MAX_AGREEMENT_MINUTES = 366 * 1440;
    /** Duración máxima de un periodo (un periodo "anual" con holgura de sobra). */
    static final int MAX_PERIOD_YEARS = 2;

    private final WorkPeriodRepository periods;
    private final IntensiveRangeRepository ranges;
    private final HolidayRepository holidays;
    private final HolidayPreloader holidayPreloader;
    private final PeriodMapper mapper;
    private final AuditService audit;

    public PeriodService(WorkPeriodRepository periods, IntensiveRangeRepository ranges, HolidayRepository holidays,
            HolidayPreloader holidayPreloader, PeriodMapper mapper, AuditService audit) {
        this.periods = periods;
        this.ranges = ranges;
        this.holidays = holidays;
        this.holidayPreloader = holidayPreloader;
        this.mapper = mapper;
        this.audit = audit;
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
        Map<UUID, List<IntensiveRange>> rangesByPeriod = ranges
                .findByPeriodIdInOrderByStartDate(all.stream().map(WorkPeriod::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(IntensiveRange::getPeriodId));
        return all.stream()
                .map(p -> mapper.toDto(p, rangesByPeriod.getOrDefault(p.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public PeriodDto get(UUID userId, UUID periodId) {
        return toDto(requireOwned(userId, periodId));
    }

    @Transactional
    public PeriodDto create(UUID userId, PeriodCreateRequest request) {
        validateParameters(request);
        IntensiveRangeValidator.validate(request.intensiveRanges(), request.startDate(), request.endDate());
        ensureNoOverlap(userId, request.startDate(), request.endDate(), null);

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
        return toDto(period);
    }

    @Transactional
    public PeriodDto update(UUID userId, UUID periodId, PeriodUpdateRequest request) {
        WorkPeriod period = requireOwned(userId, periodId);
        if (!Objects.equals(period.getVersion(), request.version())) {
            throw new ConflictException(
                    "El periodo ha cambiado en otra pestaña o dispositivo. Recarga y vuelve a intentarlo.");
        }
        validateParameters(request);
        ensureNoOverlap(userId, request.startDate(), request.endDate(), periodId);
        apply(period, request);
        periods.saveAndFlush(period);
        return toDto(period);
    }

    /** Borra el periodo con sus festivos y rangos de intensiva; los fichajes y ausencias se conservan. */
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
        return mapper.toDto(period, ranges.findByPeriodIdOrderByStartDate(period.getId()));
    }

    private void ensureNoOverlap(UUID userId, LocalDate start, LocalDate end, UUID excludeId) {
        if (periods.existsOverlapping(userId, start, end, excludeId)) {
            throw new ConflictException("Las fechas se solapan con otro de tus periodos");
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
        period.setMaxRemoteDaysMonth(p.maxRemoteDaysMonth());
        period.setOpeningBalanceMin(p.openingBalanceMin() == null ? 0 : p.openingBalanceMin());
    }
}
