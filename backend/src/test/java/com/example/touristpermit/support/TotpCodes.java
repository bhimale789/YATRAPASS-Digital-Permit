package com.example.touristpermit.support;

import com.example.touristpermit.security.mfa.Base32;
import com.example.touristpermit.security.mfa.Totp;

import java.time.Instant;

/** Generates authenticator codes in tests, as a phone app would. */
public final class TotpCodes {

    private TotpCodes() {
    }

    /** Code for the current time step plus {@code stepOffset} (within the accepted drift window). */
    public static String code(String base32Secret, long stepOffset) {
        return Totp.code(Base32.decode(base32Secret), Totp.step(Instant.now()) + stepOffset, Totp.DIGITS);
    }
}
