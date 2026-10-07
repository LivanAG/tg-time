package com.controlhorario.calendar;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import com.controlhorario.common.security.CurrentUser;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Festivos y calendario de un periodo. */
@RestController
@RequestMapping("/api/periods/{periodId}")
public class CalendarController {

    private final HolidayService holidays;
    private final CalendarService calendar;
    private final CurrentUser currentUser;

    public CalendarController(HolidayService holidays, CalendarService calendar, CurrentUser currentUser) {
        this.holidays = holidays;
        this.calendar = calendar;
        this.currentUser = currentUser;
    }

    /** Festivos del periodo, por fecha. */
    @GetMapping("/holidays")
    public List<HolidayDto> holidays(@PathVariable UUID periodId) {
        return holidays.list(currentUser.id(), periodId);
    }

    @PostMapping("/holidays")
    public ResponseEntity<HolidayDto> createHoliday(@PathVariable UUID periodId,
            @Valid @RequestBody HolidayRequest request) {
        HolidayDto created = holidays.create(currentUser.id(), periodId, request);
        return ResponseEntity.created(URI.create("/api/periods/" + periodId + "/holidays/" + created.id()))
                .body(created);
    }

    @DeleteMapping("/holidays/{holidayId}")
    public ResponseEntity<Void> deleteHoliday(@PathVariable UUID periodId, @PathVariable UUID holidayId) {
        holidays.delete(currentUser.id(), periodId, holidayId);
        return ResponseEntity.noContent().build();
    }

    /** Añade los festivos de Madrid que falten y devuelve todos los del periodo. */
    @PostMapping("/holidays/preload")
    public List<HolidayDto> preloadHolidays(@PathVariable UUID periodId) {
        return holidays.preload(currentUser.id(), periodId);
    }

    /** Un elemento por cada día del periodo. */
    @GetMapping("/calendar")
    public List<CalendarDayDto> calendar(@PathVariable UUID periodId) {
        return calendar.calendar(currentUser.id(), periodId);
    }
}
