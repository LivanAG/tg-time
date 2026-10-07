package com.controlhorario.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import com.controlhorario.common.config.AppProperties;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

class RefreshCookiesTest {

    private static AppProperties properties(boolean secure) {
        return new AppProperties("x".repeat(48), Duration.ofMinutes(15), Duration.ofDays(7), secure,
                List.of("https://horas.example.com"), false, new AppProperties.Admin(null, null, "Administrador"));
    }

    @Test
    void refreshCookieIsHttpOnlySecureStrictAndScopedToAuth() {
        ResponseCookie cookie = new RefreshCookies(properties(true)).create("token-opaco");

        assertThat(cookie.getName()).isEqualTo("refresh_token");
        assertThat(cookie.getValue()).isEqualTo("token-opaco");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Strict");
        assertThat(cookie.getPath()).isEqualTo("/api/auth");
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofDays(7));
        assertThat(cookie.toString())
                .startsWith("refresh_token=token-opaco; Path=/api/auth; Max-Age=604800; Expires=")
                .endsWith("; Secure; HttpOnly; SameSite=Strict");
    }

    @Test
    void secureCanBeDisabledOnlyByConfiguration() {
        assertThat(new RefreshCookies(properties(false)).create("t").isSecure()).isFalse();
    }

    @Test
    void clearingIsTheSameCookieWithMaxAgeZero() {
        ResponseCookie cookie = new RefreshCookies(properties(true)).clear();

        assertThat(cookie.getName()).isEqualTo("refresh_token");
        assertThat(cookie.getValue()).isEmpty();
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ZERO);
        assertThat(cookie.getPath()).isEqualTo("/api/auth");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Strict");
    }
}
