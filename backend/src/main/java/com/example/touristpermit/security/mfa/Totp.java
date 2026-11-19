package com.example.touristpermit.security.mfa;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.OptionalLong;

/**
 * Time-based one-time passwords (RFC 6238 / RFC 4226): HMAC-SHA1, 6 digits, 30-second
 * steps — compatible with Google Authenticator, Microsoft Authenticator, Authy, etc.
 */
public final class Totp {

    public static final int DIGITS = 6;
    public static final int PERIOD_SECONDS = 30;
    /** Accept codes from one step before/after to tolerate clock drift. */
    public static final int DRIFT_STEPS = 1;
    private static final int SECRET_BYTES = 20; // 160 bits, as recommended by RFC 4226
    private static final SecureRandom RANDOM = new SecureRandom();

    private Totp() {
    }

    public static String newSecret() {
        byte[] secret = new byte[SECRET_BYTES];
        RANDOM.nextBytes(secret);
        return Base32.encode(secret);
    }

    public static long step(Instant now) {
        return Math.floorDiv(now.getEpochSecond(), PERIOD_SECONDS);
    }

    /** The code for a given time step (zero-padded). */
    public static String code(byte[] secret, long step, int digits) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24) | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8) | (hash[offset + 3] & 0xFF);
            int otp = binary % (int) Math.pow(10, digits);
            return String.format("%0" + digits + "d", otp);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA1 unavailable", e);
        }
    }

    /**
     * Checks {@code code} against the steps around {@code now}. Returns the matching step,
     * or empty. Steps at or before {@code lastUsedStep} are rejected to prevent replay.
     */
    public static OptionalLong verify(String base32Secret, String code, Instant now, Long lastUsedStep) {
        if (code == null || !code.matches("\\d{" + DIGITS + "}")) {
            return OptionalLong.empty();
        }
        byte[] secret = Base32.decode(base32Secret);
        long current = step(now);
        for (long s = current - DRIFT_STEPS; s <= current + DRIFT_STEPS; s++) {
            if (lastUsedStep != null && s <= lastUsedStep) {
                continue;
            }
            // Constant-time comparison avoids leaking how many digits matched.
            if (MessageDigest.isEqual(code(secret, s, DIGITS).getBytes(StandardCharsets.US_ASCII),
                    code.getBytes(StandardCharsets.US_ASCII))) {
                return OptionalLong.of(s);
            }
        }
        return OptionalLong.empty();
    }

    /** {@code otpauth://} URI understood by authenticator apps (encoded into a QR code). */
    public static String otpauthUri(String issuer, String account, String base32Secret) {
        String label = encode(issuer) + ":" + encode(account);
        return "otpauth://totp/" + label + "?secret=" + base32Secret + "&issuer=" + encode(issuer)
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + PERIOD_SECONDS;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
