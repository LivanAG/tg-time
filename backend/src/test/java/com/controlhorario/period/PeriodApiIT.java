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
                .andExpect(jsonPath("$.maxRemoteDaysMonth").value(8))
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
    void periodsOfTheSameUserCannotOverlap() throws Exception {
        User user = newUser();
        createExcelPeriod(user);

        postPeriod(user, period("Solapado", "2027-01-01", "2027-12-31"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        // Justo a continuación sí se puede, y la lista sale con el más reciente primero.
        UUID next = createPeriod(user, period("2027-2028", "2027-05-26", "2028-05-25"));
        mvc.perform(get("/api/periods").with(as(user)))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("2027-2028"))
                .andExpect(jsonPath("$[1].name").value("2026-2027"));

        Map<String, Object> moved = period("2027-2028", "2027-05-25", "2028-05-25");
        moved.remove("preloadHolidays");
        moved.put("version", 0);
        mvc.perform(put("/api/periods/" + next).with(as(user))
                        .contentType(MediaType.APPLICATION_JSON).content(json(moved)))
                .andExpect(status().isConflict());

        // Otro usuario puede tener un periodo con las mismas fechas.
        createExcelPeriod(newUser());
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
    void deletingAPeriodKeepsWorkdaysAndAbsences() throws Exception {
        User user = newUser();
        UUID id = createExcelPeriod(user);
        putWorkday(user, LocalDate.of(2026, 5, 26), referenceDay()).andExpect(status().isOk());
        putAbsence(user, LocalDate.of(2026, 7, 10), "VACACIONES", false).andExpect(status().isOk());

        mvc.perform(delete("/api/periods/" + id).with(as(user))).andExpect(status().isNoContent());

        assertThat(workdays.existsByUserIdAndDate(user.getId(), LocalDate.of(2026, 5, 26))).isTrue();
        assertThat(absences.findByUserIdAndDate(user.getId(), LocalDate.of(2026, 7, 10))).isPresent();
        // Sin periodo, el fichaje se sigue viendo con las reglas por defecto.
        mvc.perform(get("/api/workdays/2026-05-26").with(as(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.workedMinutes").value(604));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/periods")).andExpect(status().isUnauthorized());
    }
}
