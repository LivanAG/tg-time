package com.controlhorario.auth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Rate limit de /api/auth/{login,register,refresh} (prefijo {@code app.rate-limit}).
 *
 * @param capacity   peticiones permitidas por IP y endpoint en cada periodo (10 por defecto)
 * @param period     ventana de recarga (1 minuto)
 * @param maxEntries máximo de contadores en memoria; por encima se purgan los inactivos
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
        @DefaultValue("10") int capacity,
        @DefaultValue("PT1M") Duration period,
        @DefaultValue("10000") int maxEntries) {
}
