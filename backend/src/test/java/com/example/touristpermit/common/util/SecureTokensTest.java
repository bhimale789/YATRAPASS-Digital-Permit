package com.example.touristpermit.common.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SecureTokensTest {

    @Test
    void randomTokensAreUrlSafeAndUnique() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String t = SecureTokens.randomUrlSafe(32);
            assertThat(t).matches("^[A-Za-z0-9_-]{43}$");
            assertThat(seen.add(t)).isTrue();
        }
    }

    @Test
    void sha256IsDeterministicHex() {
        assertThat(SecureTokens.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void passportMasking() {
        assertThat(Masking.passport("N1234567")).isEqualTo("****4567");
        assertThat(Masking.passport("AB")).isEqualTo("****B");
        assertThat(Masking.passport(null)).isNull();
    }
}
