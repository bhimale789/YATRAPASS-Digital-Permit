package com.example.touristpermit.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Cryptographically secure token generation and hashing helpers. */
public final class SecureTokens {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private SecureTokens() {
    }

    /** Returns a URL-safe random token carrying {@code bytes * 8} bits of entropy. */
    public static String randomUrlSafe(int bytes) {
        byte[] buf = new byte[bytes];
        RANDOM.nextBytes(buf);
        return URL_ENCODER.encodeToString(buf);
    }

    /** Lower-case hex SHA-256 of the given value. */
    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
