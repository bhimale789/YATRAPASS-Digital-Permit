package com.example.touristpermit.notification.event;

/** Published inside the transaction that marks a payment COMPLETED. */
public record PaymentCompletedEvent(Long paymentId) {
}
