package com.controlhorario.common.security;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import com.controlhorario.common.web.ValidationException;

import org.springframework.stereotype.Component;

/**
 * Política de contraseñas (registro, alta por invitación, cambio de contraseña y primer usuario):
 * entre 12 y 128 caracteres, fuera de la lista de contraseñas comunes (sin distinguir mayúsculas) y
 * distinta del email y de su parte local.
 */
@Component
public class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 128;

    static final String COMMON_PASSWORDS_RESOURCE = "security/common-passwords.txt";

    static final String REQUIRED = "La contraseña es obligatoria";
    static final String LENGTH = "La contraseña debe tener entre " + MIN_LENGTH + " y " + MAX_LENGTH + " caracteres";
    static final String COMMON = "La contraseña es demasiado común; elige otra";
    static final String SAME_AS_EMAIL = "La contraseña no puede ser igual al email";

    private final Set<String> commonPasswords;

    public PasswordPolicy() {
        this(loadCommonPasswords());
    }

    PasswordPolicy(Set<String> commonPasswords) {
        this.commonPasswords = Set.copyOf(commonPasswords);
    }

    /** Primer incumplimiento de la política, o vacío si la contraseña es válida. */
    public Optional<String> violation(String password, String email) {
        if (password == null || password.isBlank()) {
            return Optional.of(REQUIRED);
        }
        int length = password.codePointCount(0, password.length());
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            return Optional.of(LENGTH);
        }
        String normalized = password.strip().toLowerCase(Locale.ROOT);
        if (commonPasswords.contains(normalized)) {
            return Optional.of(COMMON);
        }
        if (email != null && !email.isBlank()) {
            String normalizedEmail = email.strip().toLowerCase(Locale.ROOT);
            int at = normalizedEmail.indexOf('@');
            String localPart = at > 0 ? normalizedEmail.substring(0, at) : normalizedEmail;
            if (normalized.equals(normalizedEmail) || normalized.equals(localPart)) {
                return Optional.of(SAME_AS_EMAIL);
            }
        }
        return Optional.empty();
    }

    /** Lanza {@link ValidationException} (400) sobre {@code field} si la contraseña no cumple la política. */
    public void check(String field, String password, String email) {
        Optional<String> violation = violation(password, email);
        if (violation.isPresent()) {
            throw new ValidationException(field, violation.get());
        }
    }

    int commonPasswordCount() {
        return commonPasswords.size();
    }

    static Set<String> loadCommonPasswords() {
        InputStream in = PasswordPolicy.class.getClassLoader().getResourceAsStream(COMMON_PASSWORDS_RESOURCE);
        if (in == null) {
            throw new IllegalStateException("No se encuentra la lista de contraseñas comunes: " + COMMON_PASSWORDS_RESOURCE);
        }
        Set<String> passwords = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("# ")) {
                    continue;
                }
                String value = line.strip().toLowerCase(Locale.ROOT);
                if (!value.isEmpty()) {
                    passwords.add(value);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer la lista de contraseñas comunes", e);
        }
        return passwords;
    }
}
