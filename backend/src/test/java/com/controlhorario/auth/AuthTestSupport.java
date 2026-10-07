package com.controlhorario.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.Cookie;

import com.controlhorario.TestcontainersConfiguration;
import com.controlhorario.common.audit.AuditLog;
import com.controlhorario.common.audit.AuditLogRepository;
import com.controlhorario.user.Role;
import com.controlhorario.user.User;
import com.controlhorario.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Base de los tests de integración de autenticación: Postgres en Testcontainers, MockMvc, un reloj
 * que se puede adelantar y rate limit alto (el límite real se prueba en {@link AuthRateLimitIT}).
 *
 * El reloj arranca en el instante real (no en una fecha fija) porque el JwtDecoder de Spring
 * Security valida {@code exp} contra la hora del sistema.
 */
@SpringBootTest(properties = "app.rate-limit.capacity=100000")
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, AuthTestSupport.ClockConfig.class})
public abstract class AuthTestSupport {

    /** Origen permitido en el perfil dev (frontend con Vite). */
    public static final String ORIGIN = "http://localhost:5173";
    public static final String PASSWORD = "caballo-bateria-grapa-2026";

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    @Autowired
    protected UserRepository users;

    @Autowired
    protected RefreshTokenRepository refreshTokens;

    @Autowired
    protected AuditLogRepository auditLog;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected MutableClock clock;

    @TestConfiguration(proxyBeanMethods = false)
    public static class ClockConfig {

        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(Instant.now(), ZoneId.of("Europe/Madrid"));
        }
    }

    /** En microsegundos, la precisión de timestamptz: lo leído de la BD se compara exacto. */
    @BeforeEach
    protected void resetClock() {
        clock.set(Instant.now().truncatedTo(ChronoUnit.MICROS));
    }

    protected static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    protected User createUser(String email, String password, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setName("Persona de prueba");
        user.setCompany("IZERTIS");
        user.setRole(role);
        user.setPasswordHash(passwordEncoder.encode(password));
        return users.save(user);
    }

    protected User createUser(Role role) {
        return createUser(uniqueEmail(role.name().toLowerCase()), PASSWORD, role);
    }

    protected String body(Object value) throws Exception {
        return json.writeValueAsString(value);
    }

    protected JsonNode read(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    /** POST a /api/auth/* con la cabecera Origin permitida. */
    protected static MockHttpServletRequestBuilder authPost(String path) {
        return post(path).header(HttpHeaders.ORIGIN, ORIGIN);
    }

    protected ResultActions login(String email, String password) throws Exception {
        return mvc.perform(authPost("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("email", email, "password", password))));
    }

    protected ResultActions refresh(String refreshToken) throws Exception {
        MockHttpServletRequestBuilder request = authPost("/api/auth/refresh");
        if (refreshToken != null) {
            request.cookie(new Cookie(RefreshCookies.NAME, refreshToken));
        }
        return mvc.perform(request);
    }

    protected String accessToken(MvcResult result) throws Exception {
        return read(result).get("accessToken").asText();
    }

    protected static String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    /** Valor de la cookie refresh_token de la respuesta, o null si no la pone. */
    protected static String refreshCookie(MvcResult result) {
        String header = setCookieHeader(result);
        if (header == null) {
            return null;
        }
        int end = header.indexOf(';');
        return header.substring((RefreshCookies.NAME + "=").length(), end < 0 ? header.length() : end);
    }

    protected static String setCookieHeader(MvcResult result) {
        return result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(h -> h.startsWith(RefreshCookies.NAME + "="))
                .findFirst()
                .orElse(null);
    }

    protected List<String> auditActions(UUID userId) {
        return auditLog.findByUserIdOrderByAtDesc(userId).stream().map(AuditLog::getAction).toList();
    }
}
