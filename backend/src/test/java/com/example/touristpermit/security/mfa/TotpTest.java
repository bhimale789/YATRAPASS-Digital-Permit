package com.example.touristpermit.security.mfa;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TotpTest {

    /** RFC 6238 Appendix B, SHA-1 secret "12345678901234567890", 8-digit codes. */
    @ParameterizedTest
    @CsvSource({
            "59, 94287082",
            "1111111109, 07081804",
            "1111111111, 14050471",
            "1234567890, 89005924",
            "2000000000, 69279037",
            "20000000000, 65353130",
    })
    void matchesRfc6238TestVectors(long epochSeconds, String expected) {
        byte[] secret = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
        assertThat(Totp.code(secret, Totp.step(Instant.ofEpochSecond(epochSeconds)), 8)).isEqualTo(expected);
    }

    @Test
    void verifiesWithinDriftWindowAndRejectsReplay() {
        String secret = Totp.newSecret();
        Instant now = Instant.parse("2026-11-02T06:00:10Z");
        long step = Totp.step(now);
        byte[] raw = Base32.decode(secret);

        assertThat(Totp.verify(secret, Totp.code(raw, step, 6), now, null)).hasValue(step);
        assertThat(Totp.verify(secret, Totp.code(raw, step - 1, 6), now, null)).hasValue(step - 1);
        assertThat(Totp.verify(secret, Totp.code(raw, step + 1, 6), now, null)).hasValue(step + 1);
        assertThat(Totp.verify(secret, Totp.code(raw, step - 2, 6), now, null)).isEmpty();
        // Already-used step (or older) is refused
        assertThat(Totp.verify(secret, Totp.code(raw, step, 6), now, step)).isEmpty();
        assertThat(Totp.verify(secret, "12345", now, null)).isEmpty();
        assertThat(Totp.verify(secret, "abcdef", now, null)).isEmpty();
    }

    @Test
    void base32RoundTrips() {
        byte[] data = new byte[20];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i * 37);
        }
        String encoded = Base32.encode(data);
        assertThat(encoded).matches("[A-Z2-7]{32}");
        assertThat(Base32.decode(encoded)).isEqualTo(data);
        assertThat(Base32.decode(encoded.toLowerCase().replaceAll("(.{4})", "$1 "))).isEqualTo(data);
        assertThat(Base32.encode("foobar".getBytes())).isEqualTo("MZXW6YTBOI"); // RFC 4648 vector
    }

    @Test
    void otpauthUriIsWellFormed() {
        assertThat(Totp.otpauthUri("YatraPass", "admin@dev.local", "JBSWY3DPEHPK3PXP"))
                .isEqualTo("otpauth://totp/YatraPass:admin%40dev.local?secret=JBSWY3DPEHPK3PXP&issuer=YatraPass"
                        + "&algorithm=SHA1&digits=6&period=30");
    }
}
