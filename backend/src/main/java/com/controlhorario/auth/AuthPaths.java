package com.controlhorario.auth;

import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;

/** Rutas de /api/auth que protegen los filtros de Origin y rate limit. */
final class AuthPaths {

    static final String LOGIN = "/api/auth/login";
    static final String REGISTER = "/api/auth/register";
    static final String REFRESH = "/api/auth/refresh";
    static final String LOGOUT = "/api/auth/logout";

    /** Comprobación de Origin: todo lo que abre, renueva o cierra sesión. */
    static final Set<String> ORIGIN_CHECKED = Set.of(LOGIN, REGISTER, REFRESH, LOGOUT);
    /** Rate limit por IP y endpoint. */
    static final Set<String> RATE_LIMITED = Set.of(LOGIN, REGISTER, REFRESH);

    private AuthPaths() {
    }

    /** Ruta sin el context path. */
    static String of(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }

    static boolean isPost(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod());
    }
}
