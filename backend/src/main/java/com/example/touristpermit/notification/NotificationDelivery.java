package com.example.touristpermit.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;

/** One email or SMS to send for a notification (outbox row). */
@Entity
@Table(name = "notification_deliveries")
public class NotificationDelivery {

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notification_id", nullable = false, updatable = false)
    private Notification notification;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 10)
    private DeliveryChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DeliveryStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 300)
    private String lastError;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    protected NotificationDelivery() {
    }

    public void markSent(Instant at) {
        attempts++;
        status = DeliveryStatus.SENT;
        sentAt = at;
        lastError = null;
    }

    public void markSkipped(String reason) {
        status = DeliveryStatus.SKIPPED;
        lastError = truncate(reason);
    }

    /** Records a failed attempt; retries with exponential backoff (1, 2, 4... minutes) until maxAttempts. */
    public void markAttemptFailed(String error, Instant now, int maxAttempts) {
        attempts++;
        lastError = truncate(error);
        if (attempts >= maxAttempts) {
            status = DeliveryStatus.FAILED;
        } else {
            nextAttemptAt = now.plus(Duration.ofMinutes(1L << Math.min(attempts - 1, 10)));
        }
    }

    private static String truncate(String s) {
        return s == null || s.length() <= 300 ? s : s.substring(0, 300);
    }

    public Long getId() {
        return id;
    }

    public Notification getNotification() {
        return notification;
    }

    public DeliveryChannel getChannel() {
        return channel;
    }

    public DeliveryStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }
}
