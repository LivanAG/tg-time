package com.controlhorario.common.config;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Impide arrancar en el perfil {@code prod} con secretos ausentes, cortos o de ejemplo, o con
 * la cookie de refresco sin Secure.
 */
@Component
@Profile("prod")
public class ProductionSecretsValidator {

    static final int MIN_SECRET_BYTES = 32; // 256 bits
    private static final List<String> PLACEHOLDERS = List.of("cambia-esto", "dev-only", "changeme");

    public ProductionSecretsValidator(AppProperties properties) {
        List<String> problems = problems(properties);
        if (!problems.isEmpty()) {
            throw new IllegalStateException(
                    "Configuración de producción inválida: " + String.join("; ", problems));
        }
    }

    static List<String> problems(AppProperties properties) {
        List<String> problems = new ArrayList<>();
        checkSecret("JWT_SECRET", properties.jwtSecret(), problems);
        if (!properties.cookieSecure()) {
            problems.add("APP_COOKIE_SECURE debe ser true");
        }
        if (properties.allowedOrigins() == null || properties.allowedOrigins().isEmpty()) {
            problems.add("APP_ALLOWED_ORIGINS no está definido");
        }
        return problems;
    }

    private static void checkSecret(String name, String value, List<String> problems) {
        if (value == null || value.isBlank()) {
            problems.add(name + " no está definido");
        } else if (PLACEHOLDERS.stream().anyMatch(p -> value.toLowerCase(Locale.ROOT).contains(p))) {
            problems.add(name + " conserva un valor de ejemplo");
        } else if (value.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            problems.add(name + " debe tener al menos " + MIN_SECRET_BYTES + " bytes");
        }
    }
}
