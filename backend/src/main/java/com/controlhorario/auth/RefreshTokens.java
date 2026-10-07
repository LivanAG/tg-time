package com.controlhorario.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Refresh tokens opacos: 32 bytes aleatorios en base64url sin relleno (43 caracteres). En base de
 * datos solo se guarda su SHA-256 en hexadecimal.
 */
final class RefreshTokens {

    static final int TOKEN_BYTES = 32;
    /** Longitud del token codificado; cualquier cookie más larga se descarta sin consultar la BD. */
    static final int TOKEN_LENGTH = 43;

    private RefreshTokens() {
    }

    static String generate(SecureRandom random) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha256.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    /** Descarta de entrada valores vacíos o con un formato que nunca hemos emitido. */
    static boolean isWellFormed(String token) {
        return token != null && token.length() == TOKEN_LENGTH
                && token.chars().allMatch(c -> (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                        || (c >= '0' && c <= '9') || c == '-' || c == '_');
    }
}
