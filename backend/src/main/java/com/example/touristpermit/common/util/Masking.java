package com.example.touristpermit.common.util;

/** Masks sensitive identifiers for display. */
public final class Masking {

    private Masking() {
    }

    /** {@code N1234567} -> {@code ****4567}. Returns null for null input. */
    public static String passport(String passportNumber) {
        if (passportNumber == null) {
            return null;
        }
        int visible = Math.min(4, passportNumber.length() / 2);
        return "****" + passportNumber.substring(passportNumber.length() - visible);
    }
}
