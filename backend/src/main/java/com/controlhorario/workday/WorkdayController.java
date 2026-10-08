package com.controlhorario.workday;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import com.controlhorario.common.security.CurrentUser;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registro diario: un fichaje por día y periodo, con sus pausas. {@code periodId} opcional: sin él, el
 * periodo seleccionado.
 */
@RestController
@RequestMapping("/api/workdays")
public class WorkdayController {

    private final WorkdayService service;
    private final CurrentUser currentUser;

    public WorkdayController(WorkdayService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    /** Fichajes del rango (máximo 400 días). */
    @GetMapping
    public List<WorkdayDto> list(@RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate to, @RequestParam(required = false) UUID periodId) {
        return service.list(currentUser.id(), periodId, from, to);
    }

    @GetMapping("/{date}")
    public WorkdayDto get(@PathVariable @DateTimeFormat(iso = ISO.DATE) LocalDate date,
            @RequestParam(required = false) UUID periodId) {
        return service.get(currentUser.id(), periodId, date);
    }

    /** Crea o actualiza el día (idempotente). */
    @PutMapping("/{date}")
    public WorkdayDto put(@PathVariable @DateTimeFormat(iso = ISO.DATE) LocalDate date,
            @RequestParam(required = false) UUID periodId, @Valid @RequestBody WorkdayRequest request) {
        return service.put(currentUser.id(), periodId, date, request);
    }

    @DeleteMapping("/{date}")
    public ResponseEntity<Void> delete(@PathVariable @DateTimeFormat(iso = ISO.DATE) LocalDate date,
            @RequestParam(required = false) UUID periodId) {
        service.delete(currentUser.id(), periodId, date);
        return ResponseEntity.noContent().build();
    }
}
