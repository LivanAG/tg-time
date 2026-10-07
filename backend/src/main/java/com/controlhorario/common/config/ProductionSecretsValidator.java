package com.controlhorario.common.config;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Impide arrancar en el perfil {@code prod} con secretos ausentes, cortos o de ejemplo.
 */
@Component
@Profile("prod")
public class ProductionSecretsValidator {

    static final int MIN_SECRET_BYTES = 32; // 256 bits

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
        return problems;
    }

    private static void checkSecret(String name, String value, List<String> problems) {
        if (value == null || value.isBlank()) {
            problems.add(name + " no está definido");
        } else if (value.toLowerCase(Locale.ROOT).contains("cambia-esto")) {
            problems.add(name + " conserva el valor de ejemplo");
        } else if (value.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            problems.add(name + " debe tener al menos " + MIN_SECRET_BYTES + " bytes");
        }
    }
}
