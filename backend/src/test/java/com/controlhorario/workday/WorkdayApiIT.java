package com.controlhorario.workday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.controlhorario.common.audit.AuditLogRepository;
import com.controlhorario.period.DomainApiTestSupport;
import com.controlhorario.user.User;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class WorkdayApiIT extends DomainApiTestSupport {

    private static final LocalDate MAY_26 = LocalDate.of(2026, 5, 26);

    @Autowired
    AuditLogRepository auditLog;

    @Test
    void referenceDayOfTheSpecificationGives10h04() throws Exception {
        User user = newUser();
        createExcelPeriod(user);

        putWorkday(user, MAY_26, referenceDay())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-05-26"))
                .andExpect(jsonPath("$.startTime").value("07:25"))
                .andExpect(jsonPath("$.endTime").value("17:59"))
                .andExpect(jsonPath("$.breaks", hasSize(2)))
                .andExpect(jsonPath("$.breaks[0].type").value("DESAYUNO"))
                .andExpect(jsonPath("$.breaks[0].startTime").value("12:43"))
                .andExpect(jsonPath("$.breaks[1].type").value("COMIDA"))
                .andExpect(jsonPath("$.breaks[1].endTime").value("15:32"))
                .andExpect(jsonPath("$.location").value("OFICINA"))
                .andExpect(jsonPath("$.officeStart").isEmpty())
                .andExpect(jsonPath("$.homeEnd").isEmpty())
                .andExpect(jsonPath("$.jiraMinutes").doesNotExist())
                .andExpect(jsonPath("$.notes").isEmpty())
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.totals.grossMinutes").value(634))
                .andExpect(jsonPath("$.totals.breakfastMinutes").value(19))
                .andExpect(jsonPath("$.totals.breakfastDeductedMinutes").value(0))
                .andExpect(jsonPath("$.totals.lunchMinutes").value(30))
                .andExpect(jsonPath("$.totals.lunchDeductedMinutes").value(30))
                .andExpect(jsonPath("$.totals.otherBreakMinutes").value(0))
                .andExpect(jsonPath("$.totals.workedMinutes").value(604))
                .andExpect(jsonPath("$.totals.officeMinutes").value(604))
                .andExpect(jsonPath("$.totals.remoteMinutes").value(0))
                .andExpect(jsonPath("$.warnings", hasSize(0)));

        mvc.perform(get("/api/workdays/2026-05-26").with(as(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.workedMinutes").value(604))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void reportsValidationErrorsByField() throws Exception {
        User user = newUser();
        createExcelPeriod(user);

        Map<String, Object> reversed = referenceDay();
        reversed.put("endTime", "07:00");
        putWorkday(user, MAY_26, reversed)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value("endTime"))
                .andExpect(jsonPath("$.errors[0].message").value("La salida debe ser posterior a la entrada"));

        Map<String, Object> badBreaks = referenceDay();
        badBreaks.put("breaks", List.of(
                Map.of("type", "DESAYUNO", "startTime", "06:00", "endTime", "06:20"),
                Map.of("type", "COMIDA", "startTime", "15:00", "endTime", "15:30"),
                Map.of("type", "OTRA", "startTime", "15:10", "endTime", "15:40")));
        putWorkday(user, MAY_26, badBreaks)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("breaks[0]")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("breaks[2]")));

        Map<String, Object> mixedWithoutSegments = referenceDay();
        mixedWithoutSegments.put("location", "MIXTO");
        putWorkday(user, MAY_26, mixedWithoutSegments)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("officeStart")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("homeStart")));

        Map<String, Object> missing = referenceDay();
        missing.put("startTime", null);
        putWorkday(user, MAY_26, missing)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("startTime"))
                .andExpect(jsonPath("$.errors[0].message").value("La entrada y la salida son obligatorias"));

        Map<String, Object> tooLong = referenceDay();
        tooLong.put("notes", "x".repeat(501));
        tooLong.put("breaks", Arrays.asList(Map.of("type", "COMIDA", "startTime", "15:02", "endTime", "15:32"), null));
        putWorkday(user, MAY_26, tooLong)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[*].field", hasItem("notes")));

        putWorkday(user, LocalDate.of(2026, 5, 25), referenceDay())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("date"))
                .andExpect(jsonPath("$.errors[0].message").value("El 25/05/2026 no está dentro del periodo «2026-2027»"));

        mvc.perform(get("/api/workdays/2026-05-26").with(as(user))).andExpect(status().isNotFound());
    }

    @Test
    void appliesOptimisticLocking() throws Exception {
        User user = newUser();
        createExcelPeriod(user);
        Map<String, Object> day = referenceDay();

        putWorkday(user, MAY_26, day).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(0));
        // Crear otra vez (version null) un día que ya existe.
        putWorkday(user, MAY_26, day).andExpect(status().isConflict());

        day.put("version", 0);
        day.put("endTime", "18:00");
        putWorkday(user, MAY_26, day)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.totals.workedMinutes").value(605));
        // Otra pestaña con la versión anterior.
        putWorkday(user, MAY_26, day).andExpect(status().isConflict());

        // Cambiar solo las pausas también sube la versión.
        day.put("version", 1);
        day.put("breaks", List.of(Map.of("type", "COMIDA", "startTime", "15:00", "endTime", "15:45")));
        putWorkday(user, MAY_26, day)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.breaks", hasSize(1)))
                .andExpect(jsonPath("$.totals.workedMinutes").value(635 - 45));
        day.put("version", 1);
        putWorkday(user, MAY_26, day).andExpect(status().isConflict());

        // Repetir el mismo PUT es idempotente.
        day.put("version", 2);
        putWorkday(user, MAY_26, day).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
        putWorkday(user, MAY_26, day).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));

        // Actualizar un día que no existe.
        Map<String, Object> ghost = referenceDay();
        ghost.put("version", 3);
        putWorkday(user, MAY_26.plusDays(1), ghost).andExpect(status().isConflict());
    }

    @Test
    void mixedDaysStoreTheOfficeAndHomeSegments() throws Exception {
        User user = newUser();
        createExcelPeriod(user);

        // Los tramos solo se guardan con MIXTO.
        Map<String, Object> home = referenceDay();
        home.put("location", "CASA");
        home.put("officeStart", "07:25");
        putWorkday(user, MAY_26, home)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.officeStart").isEmpty())
                .andExpect(jsonPath("$.totals.remoteMinutes").value(604))
                .andExpect(jsonPath("$.totals.officeMinutes").value(0));

        // Oficina 07:25-14:00 (con el desayuno) y casa 15:00-17:59 (con la comida). La entrada y la salida
        // del día salen de los tramos y el hueco 14:00-15:00 no cuenta.
        Map<String, Object> mixed = referenceDay();
        mixed.put("location", "MIXTO");
        mixed.put("startTime", null);
        mixed.put("endTime", null);
        mixed.put("officeStart", "07:25");
        mixed.put("officeEnd", "14:00");
        mixed.put("homeStart", "15:00");
        mixed.put("homeEnd", "17:59");
        mixed.put("notes", "  teletrabajo tardes  ");
        mixed.put("version", 0);
        putWorkday(user, MAY_26, mixed)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startTime").value("07:25"))
                .andExpect(jsonPath("$.endTime").value("17:59"))
                .andExpect(jsonPath("$.officeEnd").value("14:00"))
                .andExpect(jsonPath("$.homeStart").value("15:00"))
                .andExpect(jsonPath("$.notes").value("teletrabajo tardes"))
                .andExpect(jsonPath("$.totals.grossMinutes").value(395 + 179))
                .andExpect(jsonPath("$.totals.workedMinutes").value(574 - 30))
                .andExpect(jsonPath("$.totals.remoteMinutes").value(179 - 30))
                .andExpect(jsonPath("$.totals.officeMinutes").value(395));
        mvc.perform(get("/api/workdays/2026-05-26").with(as(user)))
                .andExpect(jsonPath("$.officeStart").value("07:25"))
                .andExpect(jsonPath("$.homeEnd").value("17:59"));

        // Una pausa en el hueco no vale.
        Map<String, Object> breakInGap = new LinkedHashMap<>(mixed);
        breakInGap.put("breaks", List.of(Map.of("type", "OTRA", "startTime", "14:15", "endTime", "14:30")));
        breakInGap.put("version", 1);
        putWorkday(user, MAY_26, breakInGap)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("La pausa debe estar dentro del tramo de oficina o del de casa"));

        Map<String, Object> office = referenceDay();
        office.put("officeStart", "07:25");
        office.put("homeEnd", "17:59");
        office.put("version", 1);
        putWorkday(user, MAY_26, office)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.location").value("OFICINA"))
                .andExpect(jsonPath("$.officeStart").isEmpty())
                .andExpect(jsonPath("$.homeEnd").isEmpty());
    }

    @Test
    void warnsWhenTheLunchIsBelowTheMinimum() throws Exception {
        User user = newUser();
        createExcelPeriod(user);
        Map<String, Object> shortLunch = referenceDay();
        shortLunch.put("breaks", List.of(Map.of("type", "COMIDA", "startTime", "14:00", "endTime", "14:20")));
        putWorkday(user, MAY_26, shortLunch)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.lunchMinutes").value(20))
                .andExpect(jsonPath("$.totals.lunchDeductedMinutes").value(30))
                .andExpect(jsonPath("$.totals.workedMinutes").value(604))
                .andExpect(jsonPath("$.warnings[0].code").value("LUNCH_BELOW_MINIMUM"))
                .andExpect(jsonPath("$.warnings[0].field").value("breaks"));
    }

    @Test
    void listsGetsAndDeletesWorkdays() throws Exception {
        User user = newUser();
        createExcelPeriod(user);
        putWorkday(user, MAY_26, referenceDay()).andExpect(status().isOk());
        putWorkday(user, MAY_26.plusDays(1), referenceDay()).andExpect(status().isOk());
        putWorkday(user, LocalDate.of(2026, 6, 1), referenceDay()).andExpect(status().isOk());

        mvc.perform(get("/api/workdays").param("from", "2026-05-01").param("to", "2026-05-31").with(as(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].date").value("2026-05-26"))
                .andExpect(jsonPath("$[1].date").value("2026-05-27"))
                .andExpect(jsonPath("$[1].totals.workedMinutes").value(604));
        mvc.perform(get("/api/workdays").param("from", "2026-06-01").param("to", "2026-05-01").with(as(user)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("to"));
        // 400 días como máximo, contando ambos extremos.
        mvc.perform(get("/api/workdays").param("from", "2026-01-01").param("to", "2027-02-05").with(as(user)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("to"));
        mvc.perform(get("/api/workdays").param("from", "2026-01-01").param("to", "2027-02-04").with(as(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
        mvc.perform(get("/api/workdays").param("from", "no-es-fecha").param("to", "2026-05-31").with(as(user)))
                .andExpect(status().isBadRequest());

        mvc.perform(delete("/api/workdays/2026-05-27").with(as(user))).andExpect(status().isNoContent());
        mvc.perform(get("/api/workdays/2026-05-27").with(as(user))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/workdays/2026-05-27").with(as(user))).andExpect(status().isNotFound());
        assertThat(auditLog.findByUserIdOrderByAtDesc(user.getId()))
                .anySatisfy(log -> {
                    assertThat(log.getAction()).isEqualTo("DELETE");
                    assertThat(log.getEntity()).isEqualTo("WORKDAY");
                });

        // Tras borrarlo se puede volver a crear con version null.
        putWorkday(user, MAY_26.plusDays(1), referenceDay())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void workdaysOfOtherUsersAreNotFound() throws Exception {
        User owner = newUser();
        User intruder = newUser();
        UUID ownerPeriod = createExcelPeriod(owner);
        putWorkday(owner, MAY_26, referenceDay()).andExpect(status().isOk());

        // Sin periodos, el intruso no puede ni leer ni fichar.
        mvc.perform(get("/api/workdays/2026-05-26").with(as(intruder)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("periodId"));
        // Con el periodo del dueño: 404, como si no existiera.
        mvc.perform(get("/api/workdays/2026-05-26").param("periodId", ownerPeriod.toString()).with(as(intruder)))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/workdays/2026-05-26").param("periodId", ownerPeriod.toString()).with(as(intruder)))
                .andExpect(status().isNotFound());
        // Con su propio periodo de las mismas fechas no ve nada del dueño.
        createExcelPeriod(intruder);
        mvc.perform(get("/api/workdays/2026-05-26").with(as(intruder))).andExpect(status().isNotFound());
        mvc.perform(get("/api/workdays").param("from", "2026-05-01").param("to", "2026-05-31").with(as(intruder)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mvc.perform(get("/api/workdays/2026-05-26").with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(0));
    }
}
