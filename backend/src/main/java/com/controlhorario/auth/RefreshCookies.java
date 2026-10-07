package com.controlhorario.auth;

import com.controlhorario.common.config.AppProperties;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Cookie del refresh token: {@code HttpOnly; Secure; SameSite=Strict; Path=/api/auth}, 7 días.
 * Secure solo se desactiva en local por http (APP_COOKIE_SECURE=false).
 */
@Component
public class RefreshCookies {

    public static final String NAME = "refresh_token";
    public static final String PATH = "/api/auth";

    private final AppProperties properties;

    public RefreshCookies(AppProperties properties) {
        this.properties = properties;
    }

    public ResponseCookie create(String token) {
        return builder(token).maxAge(properties.refreshTokenTtl()).build();
    }

    /** Misma cookie con Max-Age=0: el navegador la borra. */
    public ResponseCookie clear() {
        return builder("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder builder(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Strict")
                .path(PATH);
    }
}
