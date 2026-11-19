package com.example.touristpermit.common.util;

import java.util.Locale;

/** Builds safe LIKE patterns for user-supplied search text (used with {@code ESCAPE '\'}). */
public final class SearchPatterns {

    private static final int MAX_QUERY_LENGTH = 100;

    private SearchPatterns() {
    }

    /** Returns {@code %escaped-lowercase%}, or null when the query is blank. */
    public static String contains(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        String q = query.trim();
        if (q.length() > MAX_QUERY_LENGTH) {
            q = q.substring(0, MAX_QUERY_LENGTH);
        }
        String escaped = q.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
