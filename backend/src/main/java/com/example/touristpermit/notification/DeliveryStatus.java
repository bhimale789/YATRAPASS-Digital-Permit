package com.example.touristpermit.notification;

public enum DeliveryStatus {
    PENDING,
    SENT,
    /** Gave up after the maximum number of attempts. */
    FAILED,
    /** Could not be sent at all (no phone number, unsupported number, channel not configured). */
    SKIPPED
}
