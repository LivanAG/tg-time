package com.controlhorario.common.security;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * IP real del cliente. Con {@code server.forward-headers-strategy=framework}, getRemoteAddr()
 * ya es el primer valor de X-Forwarded-For, que en producción fija Caddy con la IP real.
 */
public final class ClientIp {

    private ClientIp() {
    }

    public static String of(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    /** IP de la petición en curso o {@code null} si no hay petición HTTP. */
    public static String current() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return of(attrs.getRequest());
        }
        return null;
    }
}
