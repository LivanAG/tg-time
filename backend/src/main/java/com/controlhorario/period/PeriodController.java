package com.controlhorario.period;

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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Periodos anuales y sus rangos de jornada intensiva. */
@RestController
@RequestMapping("/api/periods")
public class PeriodController {

    private final PeriodService service;
    private final CurrentUser currentUser;

    public PeriodController(PeriodService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    /** Periodos del usuario, el más reciente primero. */
    @GetMapping
    public List<PeriodDto> list() {
        return service.list(currentUser.id());
    }

    @PostMapping
    public ResponseEntity<PeriodDto> create(@Valid @RequestBody PeriodCreateRequest request) {
        PeriodDto created = service.create(currentUser.id(), request);
        return ResponseEntity.created(URI.create("/api/periods/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    public PeriodDto get(@PathVariable UUID id) {
        return service.get(currentUser.id(), id);
    }

    @PutMapping("/{id}")
    public PeriodDto update(@PathVariable UUID id, @Valid @RequestBody PeriodUpdateRequest request) {
        return service.update(currentUser.id(), id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(currentUser.id(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/intensive-ranges")
    public List<IntensiveRangeDto> intensiveRanges(@PathVariable UUID id) {
        return service.intensiveRanges(currentUser.id(), id);
    }

    /** Sustituye todos los rangos de intensiva y devuelve la lista guardada. */
    @PutMapping("/{id}/intensive-ranges")
    public List<IntensiveRangeDto> replaceIntensiveRanges(@PathVariable UUID id,
            @RequestBody List<IntensiveRangeDto> ranges) {
        return service.replaceIntensiveRanges(currentUser.id(), id, ranges);
    }
}
