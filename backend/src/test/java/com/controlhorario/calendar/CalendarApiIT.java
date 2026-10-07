package com.controlhorario.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.controlhorario.period.DomainApiTestSupport;
import com.controlhorario.user.User;
import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class CalendarApiIT extends DomainApiTestSupport {

    /** Festivos de es-madrid.csv entre el 26/05/2026 y el 25/05/2027 (cuatro caen en fin de semana). */
    private static final int PRELOADED_HOLIDAYS = 17;

    @Test
    void excelPeriodWithPreloadedHolidaysHas248WorkingDaysAnd1917Hours() throws Exception {
        User user = newUser();
        UUID id = createExcelPeriod(user);

        JsonNode days = read(mvc.perform(get("/api/periods/" + id + "/calendar").with(as(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(365)))
                .andExpect(jsonPath("$[0].date").value("2026-05-26"))
                .andExpect(jsonPath("$[364].date").value("2027-05-25"))
                .andReturn());

        int workingDays = 0;
        int minutes = 0;
        Map<String, JsonNode> byDate = new HashMap<>();
        for (JsonNode day : days) {
            byDate.put(day.get("date").asText(), day);
            assertThat(day.get("dayType").asText()).isIn("LABORABLE", "FIN_DE_SEMANA", "FESTIVO");
            if ("LABORABLE".equals(day.get("dayType").asText())) {
                workingDays++;
            }
            minutes += day.get("dayMinutes").asInt();
        }
        assertThat(workingDays).isEqualTo(248);
        assertThat(minutes).isEqualTo(1917 * 60);

        JsonNode nationalDay = byDate.get("2026-10-12");
        assertThat(nationalDay.get("dayType").asText()).isEqualTo("FESTIVO");
        assertThat(nationalDay.get("holidayName").asText()).isEqualTo("Fiesta Nacional de España");
        assertThat(nationalDay.get("dayMinutes").asInt()).isZero();
        assertThat(byDate.get("2026-06-12").get("dayMinutes").asInt()).isEqualTo(480);
        assertThat(byDate.get("2026-06-15").get("intensive").asBoolean()).isTrue();
        assertThat(byDate.get("2026-06-15").get("dayMinutes").asInt()).isEqualTo(420);
        assertThat(byDate.get("2026-05-30").get("dayType").asText()).isEqualTo("FIN_DE_SEMANA");
        assertThat(byDate.get("2026-05-26").get("holidayName").isNull()).isTrue();
        assertThat(byDate.get("2026-05-26").get("absence").isNull()).isTrue();
        assertThat(byDate.get("2026-05-26").get("hasWorkday").asBoolean()).isFalse();
    }

    @Test
    void calendarShowsAbsencesAndWorkdays() throws Exception {
        User user = newUser();
        UUID id = createExcelPeriod(user);
        putWorkday(user, LocalDate.of(2026, 5, 26), referenceDay()).andExpect(status().isOk());
        putAbsence(user, LocalDate.of(2026, 7, 10), "VACACIONES", false).andExpect(status().isOk());

        mvc.perform(get("/api/periods/" + id + "/calendar").with(as(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].date").value("2026-05-26"))
                .andExpect(jsonPath("$[0].hasWorkday").value(true))
                .andExpect(jsonPath("$[45].date").value("2026-07-10"))
                .andExpect(jsonPath("$[45].absence.type").value("VACACIONES"))
                .andExpect(jsonPath("$[45].absence.halfDay").value(false))
                .andExpect(jsonPath("$[45].intensive").value(true))
                .andExpect(jsonPath("$[45].hasWorkday").value(false));
    }

    @Test
    void managesTheHolidaysOfAPeriod() throws Exception {
        User user = newUser();
        UUID id = createExcelPeriod(user);
        String url = "/api/periods/" + id + "/holidays";

        JsonNode preloaded = read(mvc.perform(get(url).with(as(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(PRELOADED_HOLIDAYS)))
                .andExpect(jsonPath("$[0].date").value("2026-08-15"))
                .andExpect(jsonPath("$[0].scope").value("NACIONAL"))
                .andReturn());
        String nationalDayId = null;
        for (JsonNode h : preloaded) {
            if ("2026-10-12".equals(h.get("date").asText())) {
                nationalDayId = h.get("id").asText();
            }
        }
        assertThat(nationalDayId).isNotNull();

        String saintJohn = "{\"date\":\"2026-06-24\",\"name\":\"San Juan\",\"scope\":\"LOCAL\"}";
        mvc.perform(post(url).with(as(user)).contentType(MediaType.APPLICATION_JSON).content(saintJohn))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.date").value("2026-06-24"))
                .andExpect(jsonPath("$.name").value("San Juan"))
                .andExpect(jsonPath("$.scope").value("LOCAL"));
        mvc.perform(post(url).with(as(user)).contentType(MediaType.APPLICATION_JSON).content(saintJohn))
                .andExpect(status().isConflict());
        mvc.perform(post(url).with(as(user)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2027-06-24\",\"name\":\"Fuera\",\"scope\":\"LOCAL\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("date"));
        mvc.perform(post(url).with(as(user)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-06-25\",\"name\":\"\",\"scope\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(2)));

        // El festivo nuevo deja de ser laborable en el calendario.
        mvc.perform(get("/api/periods/" + id + "/calendar").with(as(user)))
                .andExpect(jsonPath("$[29].date").value("2026-06-24"))
                .andExpect(jsonPath("$[29].dayType").value("FESTIVO"))
                .andExpect(jsonPath("$[29].holidayName").value("San Juan"));

        mvc.perform(delete(url + "/" + nationalDayId).with(as(user))).andExpect(status().isNoContent());
        mvc.perform(delete(url + "/" + nationalDayId).with(as(user))).andExpect(status().isNotFound());
        mvc.perform(get(url).with(as(user))).andExpect(jsonPath("$", hasSize(PRELOADED_HOLIDAYS)));

        // La precarga solo añade los que faltan.
        mvc.perform(post(url + "/preload").with(as(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(PRELOADED_HOLIDAYS + 1)));
        mvc.perform(post(url + "/preload").with(as(user)))
                .andExpect(jsonPath("$", hasSize(PRELOADED_HOLIDAYS + 1)));
    }

    @Test
    void periodWithoutPreloadHasNoHolidays() throws Exception {
        User user = newUser();
        Map<String, Object> body = excelPeriod();
        body.put("preloadHolidays", false);
        UUID id = createPeriod(user, body);
        mvc.perform(get("/api/periods/" + id + "/holidays").with(as(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // Sin el campo, se precargan (por defecto true).
        Map<String, Object> next = period("2027-2028", "2027-05-26", "2028-05-25");
        next.remove("preloadHolidays");
        UUID nextId = createPeriod(user, next);
        mvc.perform(get("/api/periods/" + nextId + "/holidays").with(as(user)))
                .andExpect(jsonPath("$[0].date").value("2027-08-15"))
                // 2028 no está en el CSV: Jueves y Viernes Santo calculados (Pascua el 16/04/2028).
                .andExpect(jsonPath("$[?(@.date == '2028-04-13')].name").value("Jueves Santo"))
                .andExpect(jsonPath("$[?(@.date == '2028-04-14')].scope").value("NACIONAL"));
    }

    @Test
    void holidaysAndCalendarOfOtherUsersAreNotFound() throws Exception {
        User owner = newUser();
        User intruder = newUser();
        UUID ownerPeriod = createExcelPeriod(owner);
        UUID intruderPeriod = createPeriod(intruder, period("Mío", "2026-01-01", "2026-12-31"));
        JsonNode holidays = read(mvc.perform(get("/api/periods/" + ownerPeriod + "/holidays").with(as(owner)))
                .andReturn());
        String holidayId = holidays.get(0).get("id").asText();

        String base = "/api/periods/" + ownerPeriod;
        mvc.perform(get(base + "/holidays").with(as(intruder))).andExpect(status().isNotFound());
        mvc.perform(post(base + "/holidays").with(as(intruder)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-06-24\",\"name\":\"San Juan\",\"scope\":\"LOCAL\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete(base + "/holidays/" + holidayId).with(as(intruder))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/periods/" + intruderPeriod + "/holidays/" + holidayId).with(as(intruder)))
                .andExpect(status().isNotFound());
        mvc.perform(post(base + "/holidays/preload").with(as(intruder))).andExpect(status().isNotFound());
        mvc.perform(get(base + "/calendar").with(as(intruder))).andExpect(status().isNotFound());

        mvc.perform(get(base + "/holidays").with(as(owner)))
                .andExpect(jsonPath("$", hasSize(PRELOADED_HOLIDAYS)));
    }
}
