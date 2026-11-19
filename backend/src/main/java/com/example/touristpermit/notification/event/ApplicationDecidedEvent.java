package com.example.touristpermit.notification.event;

/** Published inside the transaction that approves or rejects an application. */
public record ApplicationDecidedEvent(Long applicationId, boolean approved) {
}
