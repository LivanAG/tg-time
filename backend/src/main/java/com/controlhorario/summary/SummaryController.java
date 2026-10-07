package com.controlhorario.summary;

import java.util.UUID;

import com.controlhorario.common.security.CurrentUser;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Resúmenes calculados: mes, periodo (hoja Horas) y portada. */
@RestController
@RequestMapping("/api/summary")
public class SummaryController {

    private final SummaryService service;
    private final CurrentUser currentUser;

    public SummaryController(SummaryService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping("/month")
    public MonthSummaryDto month(@RequestParam int year, @RequestParam int month,
            @RequestParam(required = false) UUID periodId) {
        return service.month(currentUser.id(), year, month, periodId);
    }

    @GetMapping("/period/{id}")
    public PeriodSummaryDto period(@PathVariable UUID id) {
        return service.period(currentUser.id(), id);
    }

    @GetMapping("/dashboard")
    public DashboardDto dashboard() {
        return service.dashboard(currentUser.id());
    }
}
