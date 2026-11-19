package com.example.touristpermit.security.mfa;

import java.io.ByteArrayOutputStream;
import java.util.Locale;

/** RFC 4648 Base32 (no padding), as used by authenticator apps for TOTP secrets. */
public final class Base32 {

    private static final char[] ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();

    private Base32() {
    }

    public static String encode(byte[] data) {
        StringBuilder out = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5) {
                out.append(ALPHABET[(buffer >> (bits - 5)) & 31]);
                bits -= 5;
            }
        }
        if (bits > 0) {
            out.append(ALPHABET[(buffer << (5 - bits)) & 31]);
        }
        return out.toString();
    }

    /** Decodes, ignoring case, spaces and padding. */
    public static byte[] decode(String text) {
        String clean = text.replace(" ", "").replace("=", "").toUpperCase(Locale.ROOT);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int buffer = 0;
        int bits = 0;
        for (char c : clean.toCharArray()) {
            int value = c >= 'A' && c <= 'Z' ? c - 'A' : c >= '2' && c <= '7' ? c - '2' + 26 : -1;
            if (value < 0) {
                throw new IllegalArgumentException("Invalid Base32 character");
            }
            buffer = (buffer << 5) | value;
            bits += 5;
            if (bits >= 8) {
                out.write((buffer >> (bits - 8)) & 0xFF);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }
}
