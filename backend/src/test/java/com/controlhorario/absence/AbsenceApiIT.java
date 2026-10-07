package com.controlhorario.absence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import com.controlhorario.common.audit.AuditLogRepository;
import com.controlhorario.period.DomainApiTestSupport;
import com.controlhorario.user.User;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class AbsenceApiIT extends DomainApiTestSupport {

    private static final LocalDate JULY_10 = LocalDate.of(2026, 7, 10);

    @Autowired
    AuditLogRepository auditLog;

    @Test
    void marksReadsUpdatesAndDeletesAnAbsence() throws Exception {
        User user = newUser();
        createExcelPeriod(user);

        putAbsence(user, JULY_10, "VACACIONES", false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-07-10"))
                .andExpect(jsonPath("$.type").value("VACACIONES"))
                .andExpect(jsonPath("$.halfDay").value(false))
                .andExpect(jsonPath("$.note").isEmpty());
        mvc.perform(get("/api/absences/2026-07-10").with(as(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("VACACIONES"));

        // Idempotente: el segundo PUT sustituye la ausencia del día.
        mvc.perform(put("/api/absences/2026-07-10").with(as(user)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"PUENTE\",\"halfDay\":true,\"note\":\"recuperable\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("PUENTE"))
                .andExpect(jsonPath("$.halfDay").value(true))
                .andExpect(jsonPath("$.note").value("recuperable"));
        putAbsence(user, LocalDate.of(2026, 8, 13), "VACACIONES", false).andExpect(status().isOk());

        mvc.perform(get("/api/absences").param("from", "2026-07-01").param("to", "2026-08-31").with(as(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].date").value("2026-07-10"))
                .andExpect(jsonPath("$[1].date").value("2026-08-13"));
        mvc.perform(get("/api/absences").param("from", "2026-08-01").param("to", "2026-07-01").with(as(user)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("to"));

        mvc.perform(delete("/api/absences/2026-07-10").with(as(user))).andExpect(status().isNoContent());
        mvc.perform(get("/api/absences/2026-07-10").with(as(user))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/absences/2026-07-10").with(as(user))).andExpect(status().isNotFound());
        assertThat(auditLog.findByUserIdOrderByAtDesc(user.getId()))
                .anySatisfy(log -> {
                    assertThat(log.getAction()).isEqualTo("DELETE");
                    assertThat(log.getEntity()).isEqualTo("ABSENCE");
                });
    }

    @Test
    void onlyWorkingDaysInsideAPeriodCanBeMarked() throws Exception {
        User user = newUser();
        createExcelPeriod(user);

        // Sábado.
        putAbsence(user, LocalDate.of(2026, 5, 30), "VACACIONES", false)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("date"))
                .andExpect(jsonPath("$.errors[0].message").value("Solo se pueden marcar ausencias en días laborables"));
        // Festivo precargado.
        putAbsence(user, LocalDate.of(2026, 10, 12), "VACACIONES", false)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("date"));
        // Fuera de cualquier periodo.
        putAbsence(user, LocalDate.of(2026, 5, 25), "VACACIONES", false)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("date"))
                .andExpect(jsonPath("$.errors[0].message").value("No hay ningún periodo que incluya esta fecha"));
        // Sin tipo.
        mvc.perform(put("/api/absences/2026-07-10").with(as(user)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"halfDay\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("type"));

        mvc.perform(get("/api/absences").param("from", "2026-05-01").param("to", "2026-12-31").with(as(user)))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void absencesOfOtherUsersAreNotFound() throws Exception {
        User owner = newUser();
        User intruder = newUser();
        createExcelPeriod(owner);
        createExcelPeriod(intruder);
        putAbsence(owner, JULY_10, "VACACIONES", false).andExpect(status().isOk());

        mvc.perform(get("/api/absences/2026-07-10").with(as(intruder))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/absences/2026-07-10").with(as(intruder))).andExpect(status().isNotFound());
        mvc.perform(get("/api/absences").param("from", "2026-07-01").param("to", "2026-07-31").with(as(intruder)))
                .andExpect(jsonPath("$", hasSize(0)));
        // El PUT del intruso crea su propia ausencia, nunca toca la del dueño.
        putAbsence(intruder, JULY_10, "PERMISO", false).andExpect(status().isOk());

        mvc.perform(get("/api/absences/2026-07-10").with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("VACACIONES"));
    }
}
