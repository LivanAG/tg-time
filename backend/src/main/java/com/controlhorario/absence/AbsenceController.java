package com.controlhorario.absence;

import java.time.LocalDate;
import java.util.List;

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

/** Ausencias por día: vacaciones, puentes recuperables, permisos y bajas. */
@RestController
@RequestMapping("/api/absences")
public class AbsenceController {

    private final AbsenceService service;
    private final CurrentUser currentUser;

    public AbsenceController(AbsenceService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<AbsenceDto> list(@RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate to) {
        return service.list(currentUser.id(), from, to);
    }

    @GetMapping("/{date}")
    public AbsenceDto get(@PathVariable @DateTimeFormat(iso = ISO.DATE) LocalDate date) {
        return service.get(currentUser.id(), date);
    }

    @PutMapping("/{date}")
    public AbsenceDto put(@PathVariable @DateTimeFormat(iso = ISO.DATE) LocalDate date,
            @Valid @RequestBody AbsenceRequest request) {
        return service.put(currentUser.id(), date, request);
    }

    @DeleteMapping("/{date}")
    public ResponseEntity<Void> delete(@PathVariable @DateTimeFormat(iso = ISO.DATE) LocalDate date) {
        service.delete(currentUser.id(), date);
        return ResponseEntity.noContent().build();
    }
}
