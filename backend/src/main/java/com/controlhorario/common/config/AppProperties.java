package com.controlhorario.common.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuración propia (prefijo {@code app}); en Docker llega por variables de entorno
 * (APP_ADMIN_EMAIL → app.admin.email, APP_ALLOWED_ORIGINS → app.allowed-origins, ...).
 *
 * @param jwtSecret        secreto HS256 de los access tokens (JWT_SECRET, mínimo 256 bits)
 * @param accessTokenTtl   vida del access token
 * @param refreshTokenTtl  vida del refresh token (cookie)
 * @param cookieSecure     atributo Secure de la cookie de refresco (false solo en local por http)
 * @param allowedOrigins   orígenes aceptados en la cabecera Origin de /api/auth/*
 * @param registrationOpen alta libre en /api/auth/register (cerrada por defecto)
 * @param admin            primer usuario, creado al arrancar si no hay ninguno
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String jwtSecret,
        @DefaultValue("PT15M") Duration accessTokenTtl,
        @DefaultValue("P7D") Duration refreshTokenTtl,
        @DefaultValue("true") boolean cookieSecure,
        @DefaultValue List<String> allowedOrigins,
        @DefaultValue("false") boolean registrationOpen,
        @DefaultValue Admin admin) {

    public record Admin(String email, String password, @DefaultValue("Administrador") String name) {
    }

    /** Solo el secreto (tests). */
    public static AppProperties withJwtSecret(String jwtSecret) {
        return new AppProperties(jwtSecret, Duration.ofMinutes(15), Duration.ofDays(7), true,
                List.of("https://example.com"), false, new Admin(null, null, "Administrador"));
    }
}
