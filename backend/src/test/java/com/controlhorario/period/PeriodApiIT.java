package com.controlhorario.period;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.controlhorario.absence.AbsenceRepository;
import com.controlhorario.calendar.HolidayRepository;
import com.controlhorario.common.audit.AuditLogRepository;
import com.controlhorario.user.User;
import com.controlhorario.workday.WorkdayRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.support.TransactionTemplate;

class PeriodApiIT extends DomainApiTestSupport {

    @Autowired
    HolidayRepository holidays;

    @Autowired
    IntensiveRangeRepository ranges;

    @Autowired
    WorkdayRepository workdays;

    @Autowired
    AbsenceRepository absences;

    @Autowired
    AuditLogRepository auditLog;

    @Autowired
    WorkPeriodRepository periods;

    @Autowired
    TransactionTemplate tx;

    @Test
    void theNewPeriodIsSelectedAndTheSelectionCanBeChanged() throws Exception {
        User user = newUser();
        UUID current = createExcelPeriod(user);
        mvc.perform(get("/api/periods/" + current).with(as(user))).andExpect(jsonPath("$.selected").value(true));

        UUID next = createPeriod(user, period("2027-2028", "2027-05-26", "2028-05-25"));
        mvc.perform(get("/api/periods").with(as(user)))
                .andExpect(jsonPath("$[0].id").value(next.toString()))
                .andExpect(jsonPath("$[0].selected").value(true))
                .andExpect(jsonPath("$[1].selected").value(false));

        // Seleccionar no cambia la versión (no invalida un formulario de edición abierto).
        mvc.perform(put("/api/periods/" + current + "/select").with(as(user))).andExpect(status().isNoContent());
        mvc.perform(get("/api/periods/" + current).with(as(user)))
                .andExpect(jsonPath("$.selected").value(true))
                .andExpect(jsonPath("$.version").value(0));
        mvc.perform(get("/api/periods/" + next).with(as(user))).andExpect(jsonPath("$.selected").value(false));

        // Sin ninguno marcado: el que contiene hoy (07/10/2026), aunque haya otro más reciente.
        tx.executeWithoutResult(status -> periods.clearSelected(user.getId()));
        mvc.perform(get("/api/periods").with(as(user)))
                .andExpect(jsonPath("$[0].selected").value(false))
                .andExpect(jsonPath("$[1].id").value(current.toString()))
                .andExpect(jsonPath("$[1].selected").value(true));

        // Borrado el que contiene hoy y sin ninguno marcado: el más reciente.
        mvc.perform(delete("/api/periods/" + current).with(as(user))).andExpect(status().isNoContent());
        mvc.perform(get("/api/periods").with(as(user)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].selected").value(true));

        User intruder = newUser();
        mvc.perform(put("/api/periods/" + next + "/select").with(as(intruder))).andExpect(status().isNotFound());
    }

    @Test
    void createsReadsUpdatesAndDeletesAPeriod() throws Exception {
        User user = newUser();
        String location = postPeriod(user, excelPeriod())
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("2026-2027"))
                .andExpect(jsonPath("$.startDate").value("2026-05-26"))
                .andExpect(jsonPath("$.endDate").value("2027-05-25"))
                .andExpect(jsonPath("$.agreementMinutes").value(105600))
                .andExpect(jsonPath("$.vacationDays").value(23))
                .andExpect(jsonPath("$.normalDayMinutes").value(480))
                .andExpect(jsonPath("$.intensiveDayMinutes").value(420))
                .andExpect(jsonPath("$.breakfastToleranceMin").value(20))
                .andExpect(jsonPath("$.minLunchMin").value(30))
                .andExpect(jsonPath("$.roundingStepMin").value(15))
                .andExpect(jsonPath("$.maxRemotePct").value(50))
                .andExpect(jsonPath("$.maxRemoteDaysMonth").doesNotExist())
                .andExpect(jsonPath("$.openingBalanceMin").value(0))
                .andExpect(jsonPath("$.intensiveRanges", hasSize(1)))
                .andExpect(jsonPath("$.intensiveRanges[0].startDate").value("2026-06-15"))
                .andExpect(jsonPath("$.intensiveRanges[0].endDate").value("2026-09-15"))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn().getResponse().getHeader("Location");
        UUID id = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));

        mvc.perform(get("/api/periods").with(as(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(id.toString()))
                .andExpect(jsonPath("$[0].intensiveRanges", hasSize(1)));
        mvc.perform(get("/api/periods/" + id).with(as(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("2026-2027"));

        Map<String, Object> update = excelPeriod();
        update.remove("preloadHolidays");
        update.put("name", "  Periodo 26-27  ");
        update.put("openingBalanceMin", -90);
        update.put("intensiveRanges", List.of());   // se ignora en el PUT del periodo
        update.put("version", 0);
        mvc.perform(put("/api/periods/" + id).with(as(user))
                        .contentType(MediaType.APPLICATION_JSON).content(json(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Periodo 26-27"))
                .andExpect(jsonPath("$.openingBalanceMin").value(-90))
                .andExpect(jsonPath("$.intensiveRanges", hasSize(1)))
                .andExpect(jsonPath("$.version").value(1));

        // Bloqueo optimista: la versión 0 ya no vale.
        update.put("name", "Otra pestaña");
        mvc.perform(put("/api/periods/" + id).with(as(user))
                        .contentType(MediaType.APPLICATION_JSON).content(json(update)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        update.put("version", null);
        mvc.perform(put("/api/periods/" + id).with(as(user))
                        .contentType(MediaType.APPLICATION_JSON).content(json(update)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("version")));

        assertThat(holidays.findByPeriodIdOrderByDate(id)).isNotEmpty();
        mvc.perform(delete("/api/periods/" + id).with(as(user))).andExpect(status().isNoContent());
        mvc.perform(get("/api/periods/" + id).with(as(user))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/periods/" + id).with(as(user))).andExpect(status().isNotFound());
        assertThat(holidays.findByPeriodIdOrderByDate(id)).isEmpty();
        assertThat(ranges.findByPeriodIdOrderByStartDate(id)).isEmpty();
        assertThat(auditLog.findByUserIdOrderByAtDesc(user.getId()))
                .anySatisfy(log -> {
                    assertThat(log.getAction()).isEqualTo("DELETE");
                    assertThat(log.getEntity()).isEqualTo("PERIOD");
                    assertThat(log.getEntityId()).isEqualTo(id.toString());
                });
    }

    @Test
    void validatesTheParameters() throws Exception {
        User user = newUser();

        Map<String, Object> blankName = excelPeriod();
        blankName.put("name", " ");
        postPeriod(user, blankName)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").value("El nombre es obligatorio"));

        Map<String, Object> reversed = excelPeriod();
        reversed.put("endDate", "2026-05-26");
        reversed.put("intensiveRanges", List.of());
        postPeriod(user, reversed)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("endDate"));

        Map<String, Object> tooLong = period("Larguísimo", "2026-01-01", "2030-01-01");
        postPeriod(user, tooLong)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("endDate"));

        Map<String, Object> outOfRange = excelPeriod();
        outOfRange.put("roundingStepMin", 0);
        outOfRange.put("maxRemotePct", 101);
        outOfRange.put("agreementMinutes", 0);
        outOfRange.put("normalDayMinutes", null);
        postPeriod(user, outOfRange)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(4)))
                .andExpect(jsonPath("$.errors[*].field", hasItem("roundingStepMin")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("maxRemotePct")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("agreementMinutes")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("normalDayMinutes")));

        Map<String, Object> rangeOutside = excelPeriod();
        rangeOutside.put("intensiveRanges", List.of(Map.of("startDate", "2027-05-20", "endDate", "2027-06-15")));
        postPeriod(user, rangeOutside)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("intensiveRanges[0]"));

        Map<String, Object> rangesOverlap = excelPeriod();
        rangesOverlap.put("intensiveRanges", List.of(
                Map.of("startDate", "2026-07-01", "endDate", "2026-08-31"),
                Map.of("startDate", "2026-06-15", "endDate", "2026-07-01")));
        postPeriod(user, rangesOverlap)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("intensiveRanges[0]"))
                .andExpect(jsonPath("$.errors[0].message").value("Los rangos de intensiva no pueden solaparse"));

        mvc.perform(get("/api/periods").with(as(user))).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void overlappingPeriodsHaveTheirOwnWorkdaysAndAbsences() throws Exception {
        User user = newUser();
        LocalDate june1 = LocalDate.of(2026, 6, 1);
        LocalDate july10 = LocalDate.of(2026, 7, 10);
        UUID real = createExcelPeriod(user);
        putWorkday(user, june1, referenceDay()).andExpect(status().isOk());
        putAbsence(user, july10, "VACACIONES", false).andExpect(status().isOk());

        // Un periodo de pruebas que se solapa con el real; al crearlo queda seleccionado.
        UUID test = createPeriod(user, period("Pruebas", "2026-06-01", "2027-05-31"));
        mvc.perform(get("/api/periods").with(as(user)))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("Pruebas"));
        Map<String, Object> shorter = referenceDay();
        shorter.put("endTime", "16:00");
        putWorkday(user, june1, shorter).andExpect(status().isOk());

        // El mismo día tiene un fichaje distinto en cada periodo; sin periodId, el seleccionado.
        mvc.perform(get("/api/workdays/2026-06-01").with(as(user)))
                .andExpect(jsonPath("$.endTime").value("16:00"));
        mvc.perform(get("/api/workdays/2026-06-01").param("periodId", real.toString()).with(as(user)))
                .andExpect(jsonPath("$.endTime").value("17:59"));
        mvc.perform(get("/api/absences/2026-07-10").with(as(user))).andExpect(status().isNotFound());
        mvc.perform(get("/api/absences/2026-07-10").param("periodId", real.toString()).with(as(user)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/summary/month").param("year", "2026").param("month", "6")
                        .param("periodId", real.toString()).with(as(user)))
                .andExpect(jsonPath("$.workedMinutes").value(604));
        mvc.perform(get("/api/summary/month").param("year", "2026").param("month", "6")
                        .param("periodId", test.toString()).with(as(user)))
                .andExpect(jsonPath("$.workedMinutes").value(485));

        // Fuera de las fechas del periodo no se puede fichar.
        putWorkday(user, LocalDate.of(2026, 5, 29), referenceDay())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("date"));

        // No se puede acortar un periodo dejando registros fuera.
        Map<String, Object> shrunk = period("2026-2027", "2026-06-02", "2027-05-25");
        shrunk.remove("preloadHolidays");
        shrunk.put("version", 0);
        mvc.perform(put("/api/periods/" + real).with(as(user))
                        .contentType(MediaType.APPLICATION_JSON).content(json(shrunk)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("startDate"));

        // Borrar el de pruebas no toca los registros del real.
        mvc.perform(delete("/api/periods/" + test).with(as(user))).andExpect(status().isNoContent());
        assertThat(workdays.findByPeriodIdAndDate(test, june1)).isEmpty();
        assertThat(workdays.findByPeriodIdAndDate(real, june1)).isPresent();
        assertThat(absences.findByPeriodIdAndDate(real, july10)).isPresent();
    }

    @Test
    void replacesTheIntensiveRanges() throws Exception {
        User user = newUser();
        UUID id = createExcelPeriod(user);
        String url = "/api/periods/" + id + "/intensive-ranges";

        mvc.perform(get(url).with(as(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        mvc.perform(put(url).with(as(user)).contentType(MediaType.APPLICATION_JSON).content(json(List.of(
                        Map.of("startDate", "2026-12-21", "endDate", "2026-12-31"),
                        Map.of("startDate", "2026-06-15", "endDate", "2026-09-15")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].startDate").value("2026-06-15"))
                .andExpect(jsonPath("$[1].startDate").value("2026-12-21"));
        mvc.perform(get("/api/periods/" + id).with(as(user)))
                .andExpect(jsonPath("$.intensiveRanges", hasSize(2)));

        mvc.perform(put(url).with(as(user)).contentType(MediaType.APPLICATION_JSON).content(json(List.of(
                        Map.of("startDate", "2026-09-15", "endDate", "2026-06-15")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("intensiveRanges[0]"));
        mvc.perform(get(url).with(as(user))).andExpect(jsonPath("$", hasSize(2)));

        mvc.perform(put(url).with(as(user)).contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void periodsOfOtherUsersAreNotFound() throws Exception {
        User owner = newUser();
        User intruder = newUser();
        UUID id = createExcelPeriod(owner);

        mvc.perform(get("/api/periods/" + id).with(as(intruder))).andExpect(status().isNotFound());
        Map<String, Object> update = excelPeriod();
        update.put("version", 0);
        mvc.perform(put("/api/periods/" + id).with(as(intruder))
                        .contentType(MediaType.APPLICATION_JSON).content(json(update)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/periods/" + id + "/intensive-ranges").with(as(intruder)))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/periods/" + id + "/intensive-ranges").with(as(intruder))
                        .contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/periods/" + id).with(as(intruder))).andExpect(status().isNotFound());
        mvc.perform(get("/api/periods").with(as(intruder))).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/periods/" + UUID.randomUUID()).with(as(owner))).andExpect(status().isNotFound());

        mvc.perform(get("/api/periods/" + id).with(as(owner))).andExpect(status().isOk());
        assertThat(ranges.findByPeriodIdOrderByStartDate(id)).hasSize(1);
    }

    @Test
    void deletingAPeriodDeletesItsWorkdaysAndAbsences() throws Exception {
        User user = newUser();
        UUID id = createExcelPeriod(user);
        putWorkday(user, LocalDate.of(2026, 5, 26), referenceDay()).andExpect(status().isOk());
        putAbsence(user, LocalDate.of(2026, 7, 10), "VACACIONES", false).andExpect(status().isOk());

        mvc.perform(delete("/api/periods/" + id).with(as(user))).andExpect(status().isNoContent());

        assertThat(workdays.findByPeriodIdAndDate(id, LocalDate.of(2026, 5, 26))).isEmpty();
        assertThat(absences.findByPeriodIdAndDate(id, LocalDate.of(2026, 7, 10))).isEmpty();
        // Sin periodos, el registro diario pide crear uno.
        mvc.perform(get("/api/workdays/2026-05-26").with(as(user)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("periodId"));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/periods")).andExpect(status().isUnauthorized());
    }
}
