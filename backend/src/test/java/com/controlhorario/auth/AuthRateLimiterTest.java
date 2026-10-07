package com.controlhorario.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthRateLimiterTest {

    private final AtomicLong nanos = new AtomicLong(1_000_000_000L);

    private AuthRateLimiter limiter(int capacity, int maxEntries) {
        return new AuthRateLimiter(new RateLimitProperties(capacity, Duration.ofMinutes(1), maxEntries), nanos::get);
    }

    @Test
    void allowsCapacityRequestsPerKeyAndThenAsksToWait() {
        AuthRateLimiter limiter = limiter(10, 100);
        for (int i = 0; i < 10; i++) {
            assertThat(limiter.tryConsume("/api/auth/login|10.0.0.1")).isZero();
        }
        long retryAfter = limiter.tryConsume("/api/auth/login|10.0.0.1");
        assertThat(retryAfter).isBetween(1L, 60L);

        // Otra IP u otro endpoint llevan su propio contador.
        assertThat(limiter.tryConsume("/api/auth/login|10.0.0.2")).isZero();
        assertThat(limiter.tryConsume("/api/auth/refresh|10.0.0.1")).isZero();
    }

    @Test
    void idleCountersArePurgedSoTheMapStaysBounded() {
        AuthRateLimiter limiter = limiter(10, 5);
        for (int i = 0; i < 4; i++) {
            limiter.tryConsume("key-" + i);
        }
        assertThat(limiter.size()).isEqualTo(4);

        nanos.addAndGet(Duration.ofMinutes(2).toNanos());
        limiter.cleanup();
        assertThat(limiter.size()).isZero();

        // Aunque todos estén activos, nunca pasa del máximo.
        for (int i = 0; i < 50; i++) {
            limiter.tryConsume("ip-" + i);
            assertThat(limiter.size()).isLessThanOrEqualTo(5);
        }
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThatThrownBy(() -> new AuthRateLimiter(new RateLimitProperties(0, Duration.ofMinutes(1), 10)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AuthRateLimiter(new RateLimitProperties(10, Duration.ZERO, 10)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void filterAnswers429WithRetryAfterOnlyOnLimitedEndpoints() throws Exception {
        AuthRateLimitFilter filter = new AuthRateLimitFilter(limiter(1, 100));

        MockHttpServletResponse first = run(filter, "POST", "/api/auth/login");
        assertThat(first.getStatus()).isEqualTo(200);

        MockHttpServletResponse second = run(filter, "POST", "/api/auth/login");
        assertThat(second.getStatus()).isEqualTo(429);
        assertThat(second.getHeader("Retry-After")).isNotBlank();
        assertThat(second.getContentType()).startsWith("application/problem+json");
        assertThat(second.getContentAsString()).contains("Demasiados intentos");

        // logout y el resto de la API no tienen este límite.
        assertThat(run(filter, "POST", "/api/auth/logout").getStatus()).isEqualTo(200);
        assertThat(run(filter, "POST", "/api/auth/logout").getStatus()).isEqualTo(200);
        assertThat(run(filter, "GET", "/api/auth/login").getStatus()).isEqualTo(200);
        assertThat(run(filter, "GET", "/api/auth/login").getStatus()).isEqualTo(200);
    }

    private static MockHttpServletResponse run(AuthRateLimitFilter filter, String method, String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr("192.0.2.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
