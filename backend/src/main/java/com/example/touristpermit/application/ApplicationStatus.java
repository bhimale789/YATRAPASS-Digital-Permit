package com.example.touristpermit.application;

import java.util.EnumSet;
import java.util.Set;

/**
 * Permit application lifecycle.
 * <pre>
 * PENDING ──► UNDER_REVIEW ──► APPROVED
 *    │             │
 *    ├──► REJECTED ◄┤
 *    └──► CANCELLED ◄┘
 * </pre>
 * APPROVED, REJECTED and CANCELLED are terminal.
 */
public enum ApplicationStatus {
    PENDING,
    UNDER_REVIEW,
    APPROVED,
    REJECTED,
    CANCELLED;

    /** Statuses in which an application is still being processed. */
    public static final Set<ApplicationStatus> OPEN = EnumSet.of(PENDING, UNDER_REVIEW);

    public boolean canTransitionTo(ApplicationStatus target) {
        return switch (this) {
            case PENDING -> target == UNDER_REVIEW || target == REJECTED || target == CANCELLED;
            case UNDER_REVIEW -> target == APPROVED || target == REJECTED || target == CANCELLED;
            case APPROVED, REJECTED, CANCELLED -> false;
        };
    }

    public boolean isOpen() {
        return OPEN.contains(this);
    }
}
