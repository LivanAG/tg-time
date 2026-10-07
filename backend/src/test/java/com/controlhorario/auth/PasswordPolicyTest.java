package com.controlhorario.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.controlhorario.common.security.PasswordPolicy;
import com.controlhorario.common.web.FieldErrorDto;
import com.controlhorario.common.web.ValidationException;

import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    private static final String EMAIL = "livan.aranda@example.com";
    private static final String LENGTH = "La contraseña debe tener entre 12 y 128 caracteres";
    private static final String COMMON = "La contraseña es demasiado común; elige otra";
    private static final String SAME_AS_EMAIL = "La contraseña no puede ser igual al email";

    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void commonPasswordListHasAtLeastAThousandEntries() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("security/common-passwords.txt")) {
            assertThat(in).isNotNull();
            List<String> entries = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines()
                    .filter(line -> !line.startsWith("# ") && !line.isBlank())
                    .toList();
            assertThat(entries).hasSizeGreaterThanOrEqualTo(1000).doesNotHaveDuplicates();
            assertThat(entries.stream().filter(e -> e.length() >= PasswordPolicy.MIN_LENGTH).count())
                    .isGreaterThanOrEqualTo(1000);
        }
    }

    @Test
    void acceptsALongUncommonPassword() {
        assertThat(policy.violation("caballo-bateria-grapa-2026", EMAIL)).isEmpty();
        assertThat(policy.violation("a".repeat(11) + "ñ", EMAIL)).isEmpty();
        assertThat(policy.violation("x".repeat(128), EMAIL)).isEmpty();
        // La contraseña local de docker compose tiene que pasar la política.
        assertThat(policy.violation("controlhorario-dev", "admin@controlhorario.local")).isEmpty();
    }

    @Test
    void rejectsTooShortOrTooLong() {
        assertThat(policy.violation("", EMAIL)).contains("La contraseña es obligatoria");
        assertThat(policy.violation(null, EMAIL)).contains("La contraseña es obligatoria");
        assertThat(policy.violation("   ", EMAIL)).contains("La contraseña es obligatoria");
        assertThat(policy.violation("once-chars!", EMAIL)).contains(LENGTH);
        assertThat(policy.violation("x".repeat(129), EMAIL)).contains(LENGTH);
        // Se cuentan caracteres, no unidades UTF-16: 11 emojis siguen siendo cortos.
        assertThat(policy.violation("😀".repeat(11), EMAIL)).contains(LENGTH);
    }

    @Test
    void rejectsCommonPasswordsIgnoringCase() {
        assertThat(policy.violation("passwordpassword", EMAIL)).contains(COMMON);
        assertThat(policy.violation("PassWordPassWord", EMAIL)).contains(COMMON);
        assertThat(policy.violation("123456789012", EMAIL)).contains(COMMON);
        assertThat(policy.violation("Contraseña123", EMAIL)).contains(COMMON);
        assertThat(policy.violation("QWERTYUIOP123", EMAIL)).contains(COMMON);
        assertThat(policy.violation("unbelievable", EMAIL)).contains(COMMON);
    }

    @Test
    void rejectsTheEmailOrItsLocalPart() {
        assertThat(policy.violation("Livan.Aranda@Example.com", EMAIL)).contains(SAME_AS_EMAIL);
        assertThat(policy.violation("LIVAN.ARANDA", EMAIL)).contains(SAME_AS_EMAIL);
        assertThat(policy.violation("livan.aranda.2026", EMAIL)).isEmpty();
    }

    @Test
    void checkThrowsAValidationErrorOnTheGivenField() {
        assertThatThrownBy(() -> policy.check("newPassword", "corta", EMAIL))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((ValidationException) e).getErrors())
                        .containsExactly(new FieldErrorDto("newPassword", LENGTH)));
        assertThatCode(() -> policy.check("password", "caballo-bateria-grapa-2026", EMAIL))
                .doesNotThrowAnyException();
    }
}
