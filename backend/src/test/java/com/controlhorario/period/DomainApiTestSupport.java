package com.controlhorario.period;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.controlhorario.TestcontainersConfiguration;
import com.controlhorario.user.User;
import com.controlhorario.user.UserRepository;
import com.controlhorario.workday.calc.BreakInput;
import com.controlhorario.workday.calc.WorkdayInput;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Base de los tests de integración del dominio: Postgres de Testcontainers, "hoy" fijo en
 * 2026-10-07 10:00 (Europe/Madrid) y un usuario nuevo por test autenticado con un JWT de prueba.
 * Todas las subclases comparten el mismo contexto de Spring.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, DomainApiTestSupport.FixedClockConfiguration.class})
public abstract class DomainApiTestSupport {

    public static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    public static final Instant NOW = ZonedDateTime.of(2026, 10, 7, 10, 0, 0, 0, MADRID).toInstant();
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    @TestConfiguration(proxyBeanMethods = false)
    public static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, MADRID);
        }
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected UserRepository users;

    protected User newUser() {
        User user = new User();
        user.setEmail("user-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash("no-se-usa-en-estos-tests");
        user.setName("Usuario de prueba");
        return users.save(user);
    }

    protected static RequestPostProcessor as(User user) {
        return jwt().jwt(j -> j.subject(user.getId().toString()).claim("role", "USER"));
    }

    protected String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    protected JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    /** Parámetros del periodo del Excel (los del ejemplo de docs/API.md). */
    protected static Map<String, Object> excelPeriod() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "2026-2027");
        body.put("startDate", "2026-05-26");
        body.put("endDate", "2027-05-25");
        body.put("agreementMinutes", 105600);
        body.put("vacationDays", 23);
        body.put("normalDayMinutes", 480);
        body.put("intensiveDayMinutes", 420);
        body.put("breakfastToleranceMin", 20);
        body.put("minLunchMin", 30);
        body.put("roundingStepMin", 15);
        body.put("maxRemotePct", 50);
        body.put("maxRemoteDaysMonth", 8);
        body.put("openingBalanceMin", 0);
        body.put("intensiveRanges", List.of(Map.of("startDate", "2026-06-15", "endDate", "2026-09-15")));
        body.put("preloadHolidays", true);
        return body;
    }

    protected static Map<String, Object> period(String name, String start, String end) {
        Map<String, Object> body = excelPeriod();
        body.put("name", name);
        body.put("startDate", start);
        body.put("endDate", end);
        body.put("intensiveRanges", List.of());
        return body;
    }

    protected ResultActions postPeriod(User user, Map<String, Object> body) throws Exception {
        return mvc.perform(post("/api/periods").with(as(user))
                .contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    /** Crea el periodo y devuelve su id. */
    protected UUID createPeriod(User user, Map<String, Object> body) throws Exception {
        MvcResult result = postPeriod(user, body).andExpect(status().isCreated()).andReturn();
        return UUID.fromString(read(result).get("id").asText());
    }

    protected UUID createExcelPeriod(User user) throws Exception {
        return createPeriod(user, excelPeriod());
    }

    /** Cuerpo del PUT de un fichaje a partir de una entrada del cálculo. */
    protected static Map<String, Object> workdayBody(WorkdayInput input, Long version) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("startTime", input.start().format(HH_MM));
        body.put("endTime", input.end().format(HH_MM));
        List<Map<String, Object>> breaks = new ArrayList<>();
        for (BreakInput b : input.breaks()) {
            breaks.add(Map.of("type", b.type().name(), "startTime", b.start().format(HH_MM),
                    "endTime", b.end().format(HH_MM)));
        }
        body.put("breaks", breaks);
        body.put("location", input.location().name());
        body.put("remoteMinutes", input.remoteMinutes());
        body.put("jiraMinutes", input.jiraMinutes());
        body.put("izertiaMinutes", input.izertiaMinutes());
        body.put("notes", null);
        body.put("version", version);
        return body;
    }

    /** Día de referencia de la especificación: 26/05/2026, 07:25-17:59, desayuno de 19 min y comida de 30. */
    protected static Map<String, Object> referenceDay() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("startTime", "07:25");
        body.put("endTime", "17:59");
        body.put("breaks", List.of(
                Map.of("type", "DESAYUNO", "startTime", "12:43", "endTime", "13:02"),
                Map.of("type", "COMIDA", "startTime", "15:02", "endTime", "15:32")));
        body.put("location", "OFICINA");
        body.put("remoteMinutes", null);
        body.put("jiraMinutes", 660);
        body.put("izertiaMinutes", 660);
        body.put("notes", null);
        body.put("version", null);
        return body;
    }

    protected ResultActions putWorkday(User user, LocalDate date, Map<String, Object> body) throws Exception {
        return mvc.perform(put("/api/workdays/" + date).with(as(user))
                .contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    protected ResultActions putAbsence(User user, LocalDate date, String type, boolean halfDay) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", type);
        body.put("halfDay", halfDay);
        body.put("note", null);
        return mvc.perform(put("/api/absences/" + date).with(as(user))
                .contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }
}
