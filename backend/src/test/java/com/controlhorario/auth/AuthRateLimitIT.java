package com.controlhorario.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import com.controlhorario.TestcontainersConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Rate limit real (10 por minuto, configuración por defecto). Cada test usa sus propias IPs para
 * no depender del orden ni de otras clases que compartan el contexto.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthRateLimitIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Test
    void eleventhLoginWithinAMinuteIs429PerIpAndEndpoint() throws Exception {
        String ip = "203.0.113.11";
        for (int i = 0; i < 10; i++) {
            login(ip).andExpect(status().isUnauthorized());
        }

        String retryAfter = login(ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andReturn().getResponse().getHeader(HttpHeaders.RETRY_AFTER);
        assertThat(Long.parseLong(retryAfter)).isBetween(1L, 60L);

        // El límite es por endpoint y por IP.
        mvc.perform(post("/api/auth/refresh").header(HttpHeaders.ORIGIN, AuthTestSupport.ORIGIN).with(fromIp(ip)))
                .andExpect(status().isUnauthorized());
        login("203.0.113.12").andExpect(status().isUnauthorized());
        // logout no tiene límite.
        for (int i = 0; i < 12; i++) {
            mvc.perform(post("/api/auth/logout").header(HttpHeaders.ORIGIN, AuthTestSupport.ORIGIN).with(fromIp(ip)))
                    .andExpect(status().isNoContent());
        }
    }

    @Test
    void registerAndRefreshAreAlsoLimited() throws Exception {
        String ip = "203.0.113.21";
        for (int i = 0; i < 10; i++) {
            mvc.perform(post("/api/auth/register").header(HttpHeaders.ORIGIN, AuthTestSupport.ORIGIN).with(fromIp(ip)))
                    .andExpect(status().isForbidden()); // registro cerrado por defecto
        }
        mvc.perform(post("/api/auth/register").header(HttpHeaders.ORIGIN, AuthTestSupport.ORIGIN).with(fromIp(ip)))
                .andExpect(status().isTooManyRequests());

        String other = "203.0.113.22";
        for (int i = 0; i < 10; i++) {
            mvc.perform(post("/api/auth/refresh").header(HttpHeaders.ORIGIN, AuthTestSupport.ORIGIN).with(fromIp(other)))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/refresh").header(HttpHeaders.ORIGIN, AuthTestSupport.ORIGIN).with(fromIp(other)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
    }

    @Test
    void requestsRejectedByOriginDoNotConsumeTheLimit() throws Exception {
        String ip = "203.0.113.31";
        for (int i = 0; i < 15; i++) {
            mvc.perform(post("/api/auth/login").header(HttpHeaders.ORIGIN, "https://evil.example").with(fromIp(ip)))
                    .andExpect(status().isForbidden());
        }
        login(ip).andExpect(status().isUnauthorized());
    }

    private ResultActions login(String ip) throws Exception {
        return mvc.perform(post("/api/auth/login")
                .header(HttpHeaders.ORIGIN, AuthTestSupport.ORIGIN)
                .with(fromIp(ip))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", "nadie@example.com", "password", "no-existe-1234"))));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor fromIp(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }
}
