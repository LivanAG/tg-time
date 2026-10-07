package com.controlhorario.absence;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.controlhorario.calendar.calc.PeriodCalendar;
import com.controlhorario.common.audit.AuditService;
import com.controlhorario.common.web.NotFoundException;
import com.controlhorario.common.web.ValidationException;
import com.controlhorario.period.PeriodRulesFactory;
import com.controlhorario.period.WorkPeriod;
import com.controlhorario.period.WorkPeriodRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ausencias por día del usuario. Solo se pueden marcar en días laborables de un periodo. */
@Service
public class AbsenceService {

    private final AbsenceRepository absences;
    private final WorkPeriodRepository periods;
    private final PeriodRulesFactory rulesFactory;
    private final AbsenceMapper mapper;
    private final AuditService audit;

    public AbsenceService(AbsenceRepository absences, WorkPeriodRepository periods, PeriodRulesFactory rulesFactory,
            AbsenceMapper mapper, AuditService audit) {
        this.absences = absences;
        this.periods = periods;
        this.rulesFactory = rulesFactory;
        this.mapper = mapper;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<AbsenceDto> list(UUID userId, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new ValidationException("to", "La fecha final debe ser igual o posterior a la inicial");
        }
        return mapper.toDtos(absences.findByUserIdAndDateBetweenOrderByDate(userId, from, to));
    }

    @Transactional(readOnly = true)
    public AbsenceDto get(UUID userId, LocalDate date) {
        return mapper.toDto(require(userId, date));
    }

    /** Crea o sustituye la ausencia del día (idempotente). */
    @Transactional
    public AbsenceDto put(UUID userId, LocalDate date, AbsenceRequest request) {
        WorkPeriod period = periods.findContaining(userId, date)
                .orElseThrow(() -> new ValidationException("date", "No hay ningún periodo que incluya esta fecha"));
        PeriodCalendar calendar = rulesFactory.calendarFor(period);
        if (!calendar.isWorkingDay(date)) {
            throw new ValidationException("date", "Solo se pueden marcar ausencias en días laborables");
        }
        Absence absence = absences.findByUserIdAndDate(userId, date).orElseGet(() -> new Absence(userId, date));
        absence.setType(request.type());
        absence.setHalfDay(Boolean.TRUE.equals(request.halfDay()));
        absence.setNote(blankToNull(request.note()));
        return mapper.toDto(absences.saveAndFlush(absence));
    }

    @Transactional
    public void delete(UUID userId, LocalDate date) {
        Absence absence = require(userId, date);
        absences.delete(absence);
        absences.flush();
        audit.record(userId, "DELETE", "ABSENCE", absence.getId().toString());
    }

    private Absence require(UUID userId, LocalDate date) {
        return absences.findByUserIdAndDate(userId, date)
                .orElseThrow(() -> new NotFoundException("No hay ninguna ausencia ese día"));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
