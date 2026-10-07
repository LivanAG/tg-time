package com.controlhorario.calendar;

import java.util.List;
import java.util.UUID;

import com.controlhorario.common.audit.AuditService;
import com.controlhorario.common.web.ConflictException;
import com.controlhorario.common.web.NotFoundException;
import com.controlhorario.common.web.ValidationException;
import com.controlhorario.period.PeriodService;
import com.controlhorario.period.WorkPeriod;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Festivos de un periodo del usuario (periodo ajeno o inexistente: 404). */
@Service
public class HolidayService {

    private final PeriodService periods;
    private final HolidayRepository holidays;
    private final HolidayPreloader preloader;
    private final HolidayMapper mapper;
    private final AuditService audit;

    public HolidayService(PeriodService periods, HolidayRepository holidays, HolidayPreloader preloader,
            HolidayMapper mapper, AuditService audit) {
        this.periods = periods;
        this.holidays = holidays;
        this.preloader = preloader;
        this.mapper = mapper;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<HolidayDto> list(UUID userId, UUID periodId) {
        periods.requireOwned(userId, periodId);
        return mapper.toDtos(holidays.findByPeriodIdOrderByDate(periodId));
    }

    @Transactional
    public HolidayDto create(UUID userId, UUID periodId, HolidayRequest request) {
        WorkPeriod period = periods.requireOwned(userId, periodId);
        if (!period.contains(request.date())) {
            throw new ValidationException("date", "El festivo debe estar dentro del periodo");
        }
        if (holidays.existsByPeriodIdAndDate(periodId, request.date())) {
            throw new ConflictException("Ya hay un festivo en esa fecha");
        }
        Holiday holiday = new Holiday(periodId, request.date(), request.name().strip(), request.scope());
        return mapper.toDto(holidays.saveAndFlush(holiday));
    }

    @Transactional
    public void delete(UUID userId, UUID periodId, UUID holidayId) {
        periods.requireOwned(userId, periodId);
        Holiday holiday = holidays.findByIdAndPeriodId(holidayId, periodId)
                .orElseThrow(() -> new NotFoundException("Festivo no encontrado"));
        holidays.delete(holiday);
        holidays.flush();
        audit.record(userId, "DELETE", "HOLIDAY", holidayId.toString());
    }

    /** Añade los festivos precargados que falten y devuelve todos los del periodo. */
    @Transactional
    public List<HolidayDto> preload(UUID userId, UUID periodId) {
        WorkPeriod period = periods.requireOwned(userId, periodId);
        preloader.preload(periodId, period.getStartDate(), period.getEndDate());
        holidays.flush();
        return mapper.toDtos(holidays.findByPeriodIdOrderByDate(periodId));
    }
}
