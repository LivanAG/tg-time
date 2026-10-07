package com.controlhorario.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ProductionSecretsValidatorTest {

    // 48 bytes aleatorios en base64, como `openssl rand -base64 48`.
    private static final String STRONG = "q1Z3m8b0YV7c2nT9xK4pL6sR1wE5uH8jA3dF0gB7iCoV2yN6tQ9rM4kJ1hG5fD8s";

    @Test
    void acceptsStrongSecret() {
        assertThatCode(() -> new ProductionSecretsValidator(new AppProperties(STRONG)))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingSecret() {
        assertThat(ProductionSecretsValidator.problems(new AppProperties(" ")))
                .containsExactly("JWT_SECRET no está definido");
        assertThat(ProductionSecretsValidator.problems(new AppProperties(null)))
                .containsExactly("JWT_SECRET no está definido");
    }

    @Test
    void rejectsPlaceholderValue() {
        assertThat(ProductionSecretsValidator.problems(new AppProperties("cambia-esto")))
                .containsExactly("JWT_SECRET conserva el valor de ejemplo");
    }

    @Test
    void rejectsSecretShorterThan256Bits() {
        String shortSecret = "a".repeat(ProductionSecretsValidator.MIN_SECRET_BYTES - 1);
        assertThat(ProductionSecretsValidator.problems(new AppProperties(shortSecret)))
                .containsExactly("JWT_SECRET debe tener al menos 32 bytes");
    }

    @Test
    void constructorFailsSoTheApplicationDoesNotStart() {
        assertThatThrownBy(() -> new ProductionSecretsValidator(new AppProperties("")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }
}
