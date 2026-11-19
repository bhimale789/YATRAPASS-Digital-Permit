package com.example.touristpermit.common.util;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Year;

/**
 * Human-readable reference numbers backed by PostgreSQL sequences (race-free).
 * These numbers are identifiers for display, never security tokens.
 */
@Component
public class ReferenceNumberGenerator {

    public enum Kind {
        APPLICATION("APP", "application_number_seq"),
        PERMIT("PRM", "permit_number_seq");

        private final String prefix;
        private final String sequence;

        Kind(String prefix, String sequence) {
            this.prefix = prefix;
            this.sequence = sequence;
        }
    }

    private final EntityManager entityManager;
    private final Clock clock;

    public ReferenceNumberGenerator(EntityManager entityManager, Clock clock) {
        this.entityManager = entityManager;
        this.clock = clock;
    }

    /** e.g. {@code PRM-2026-000001}. */
    public String next(Kind kind) {
        Number value = (Number) entityManager
                .createNativeQuery("SELECT nextval('" + kind.sequence + "')")
                .getSingleResult();
        return "%s-%d-%06d".formatted(kind.prefix, Year.now(clock).getValue(), value.longValue());
    }
}
