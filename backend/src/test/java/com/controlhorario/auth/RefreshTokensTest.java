package com.controlhorario.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class RefreshTokensTest {

    @Test
    void generatesThirtyTwoRandomBytesAsBase64UrlWithoutPadding() {
        SecureRandom random = new SecureRandom();
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String token = RefreshTokens.generate(random);
            assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]+").doesNotContain("=");
            assertThat(Base64.getUrlDecoder().decode(token)).hasSize(32);
            assertThat(RefreshTokens.isWellFormed(token)).isTrue();
            tokens.add(token);
        }
        assertThat(tokens).hasSize(1000);
    }

    @Test
    void hashIsLowercaseHexSha256() {
        assertThat(RefreshTokens.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(RefreshTokens.hash(RefreshTokens.generate(new SecureRandom()))).hasSize(64).matches("[0-9a-f]+");
    }

    @Test
    void rejectsMalformedTokensBeforeTouchingTheDatabase() {
        assertThat(RefreshTokens.isWellFormed(null)).isFalse();
        assertThat(RefreshTokens.isWellFormed("")).isFalse();
        assertThat(RefreshTokens.isWellFormed("a".repeat(42))).isFalse();
        assertThat(RefreshTokens.isWellFormed("a".repeat(44))).isFalse();
        assertThat(RefreshTokens.isWellFormed("a".repeat(42) + "=")).isFalse();
        assertThat(RefreshTokens.isWellFormed("a".repeat(42) + "ñ")).isFalse();
        assertThat(RefreshTokens.isWellFormed("a".repeat(41) + "-_")).isTrue();
    }
}
