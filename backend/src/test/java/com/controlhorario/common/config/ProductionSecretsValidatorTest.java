package com.controlhorario.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

class ProductionSecretsValidatorTest {

    // 48 bytes aleatorios en base64, como `openssl rand -base64 48`.
    private static final String STRONG = "q1Z3m8b0YV7c2nT9xK4pL6sR1wE5uH8jA3dF0gB7iCoV2yN6tQ9rM4kJ1hG5fD8s";

    private static AppProperties props(String secret, boolean cookieSecure, List<String> origins) {
        return new AppProperties(secret, Duration.ofMinutes(15), Duration.ofDays(7), cookieSecure, origins, false,
                new AppProperties.Admin(null, null, "Administrador"));
    }

    @Test
    void acceptsStrongConfiguration() {
        assertThatCode(() -> new ProductionSecretsValidator(AppProperties.withJwtSecret(STRONG)))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingSecret() {
        assertThat(ProductionSecretsValidator.problems(AppProperties.withJwtSecret(" ")))
                .containsExactly("JWT_SECRET no está definido");
        assertThat(ProductionSecretsValidator.problems(AppProperties.withJwtSecret(null)))
                .containsExactly("JWT_SECRET no está definido");
    }

    @Test
    void rejectsPlaceholderValues() {
        assertThat(ProductionSecretsValidator.problems(AppProperties.withJwtSecret("cambia-esto")))
                .containsExactly("JWT_SECRET conserva un valor de ejemplo");
        // El valor por defecto de docker compose en local nunca puede llegar a producción.
        assertThat(ProductionSecretsValidator.problems(
                AppProperties.withJwtSecret("dev-only-jwt-secret-not-for-production-000000")))
                .containsExactly("JWT_SECRET conserva un valor de ejemplo");
    }

    @Test
    void rejectsSecretShorterThan256Bits() {
        String shortSecret = "a".repeat(ProductionSecretsValidator.MIN_SECRET_BYTES - 1);
        assertThat(ProductionSecretsValidator.problems(AppProperties.withJwtSecret(shortSecret)))
                .containsExactly("JWT_SECRET debe tener al menos 32 bytes");
    }

    @Test
    void rejectsInsecureCookieAndMissingOrigins() {
        assertThat(ProductionSecretsValidator.problems(props(STRONG, false, List.of())))
                .containsExactly("APP_COOKIE_SECURE debe ser true", "APP_ALLOWED_ORIGINS no está definido");
    }

    @Test
    void constructorFailsSoTheApplicationDoesNotStart() {
        assertThatThrownBy(() -> new ProductionSecretsValidator(AppProperties.withJwtSecret("")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }
}
