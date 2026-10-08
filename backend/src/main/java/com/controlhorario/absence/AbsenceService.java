package com.controlhorario.absence;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.controlhorario.calendar.calc.PeriodCalendar;
import com.controlhorario.common.audit.AuditService;
import com.controlhorario.common.web.NotFoundException;
import com.controlhorario.common.web.ValidationException;
import com.controlhorario.period.PeriodRulesFactory;
import com.controlhorario.period.PeriodService;
import com.controlhorario.period.WorkPeriod;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ausencias por día. Cada periodo tiene las suyas: todas las operaciones van sobre el periodo indicado o,
 * sin él, sobre el seleccionado. Solo se pueden marcar en días laborables del periodo.
 */
@Service
public class AbsenceService {

    private final AbsenceRepository absences;
    private final PeriodService periods;
    private final PeriodRulesFactory rulesFactory;
    private final AbsenceMapper mapper;
    private final AuditService audit;

    public AbsenceService(AbsenceRepository absences, PeriodService periods, PeriodRulesFactory rulesFactory,
            AbsenceMapper mapper, AuditService audit) {
        this.absences = absences;
        this.periods = periods;
        this.rulesFactory = rulesFactory;
        this.mapper = mapper;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<AbsenceDto> list(UUID userId, UUID periodId, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new ValidationException("to", "La fecha final debe ser igual o posterior a la inicial");
        }
        WorkPeriod period = periods.resolve(userId, periodId);
        return mapper.toDtos(absences.findByPeriodIdAndDateBetweenOrderByDate(period.getId(), from, to));
    }

    @Transactional(readOnly = true)
    public AbsenceDto get(UUID userId, UUID periodId, LocalDate date) {
        return mapper.toDto(require(periods.resolve(userId, periodId), date));
    }

    /** Crea o sustituye la ausencia del día (idempotente). */
    @Transactional
    public AbsenceDto put(UUID userId, UUID periodId, LocalDate date, AbsenceRequest request) {
        WorkPeriod period = periods.resolve(userId, periodId);
        PeriodService.requireDateIn(period, date);
        PeriodCalendar calendar = rulesFactory.calendarFor(period);
        if (!calendar.isWorkingDay(date)) {
            throw new ValidationException("date", "Solo se pueden marcar ausencias en días laborables");
        }
        Absence absence = absences.findByPeriodIdAndDate(period.getId(), date)
                .orElseGet(() -> new Absence(userId, period.getId(), date));
        absence.setType(request.type());
        absence.setHalfDay(Boolean.TRUE.equals(request.halfDay()));
        absence.setNote(blankToNull(request.note()));
        return mapper.toDto(absences.saveAndFlush(absence));
    }

    @Transactional
    public void delete(UUID userId, UUID periodId, LocalDate date) {
        Absence absence = require(periods.resolve(userId, periodId), date);
        absences.delete(absence);
        absences.flush();
        audit.record(userId, "DELETE", "ABSENCE", absence.getId().toString());
    }

    private Absence require(WorkPeriod period, LocalDate date) {
        return absences.findByPeriodIdAndDate(period.getId(), date)
                .orElseThrow(() -> new NotFoundException("No hay ninguna ausencia ese día"));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
