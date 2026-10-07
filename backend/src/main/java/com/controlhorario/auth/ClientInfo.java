package com.controlhorario.auth;

import jakarta.servlet.http.HttpServletRequest;

import com.controlhorario.common.security.ClientIp;

import org.springframework.http.HttpHeaders;

/** Navegador e IP de quien abre o renueva la sesión (se guardan con el refresh token). */
public record ClientInfo(String userAgent, String ip) {

    static final int MAX_USER_AGENT = 255;
    static final int MAX_IP = 45;

    public static ClientInfo of(HttpServletRequest request) {
        return new ClientInfo(truncate(request.getHeader(HttpHeaders.USER_AGENT), MAX_USER_AGENT),
                truncate(ClientIp.of(request), MAX_IP));
    }

    private static String truncate(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
