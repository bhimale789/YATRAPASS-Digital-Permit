package com.example.touristpermit.permit;

/**
 * Issued permit lifecycle.
 * <pre>
 * ACTIVE ──► EXPIRED
 *   ├──────► CANCELLED
 *   └──────► COMPLETED
 * </pre>
 * All non-ACTIVE statuses are terminal.
 */
public enum PermitStatus {
    ACTIVE,
    EXPIRED,
    CANCELLED,
    COMPLETED;

    public boolean canTransitionTo(PermitStatus target) {
        return this == ACTIVE && target != ACTIVE;
    }
}
