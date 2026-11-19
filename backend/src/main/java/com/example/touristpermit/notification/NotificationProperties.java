package com.example.touristpermit.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Email/SMS notification settings. In-app notifications are always on; email and SMS are
 * only queued for channels that are enabled.
 */
@ConfigurationProperties(prefix = "app.notifications")
public record NotificationProperties(Email email, Sms sms, List<Integer> expiryReminderDays, int maxAttempts) {

    /** SMTP server settings are the standard {@code spring.mail.*} (SPRING_MAIL_HOST, ...). */
    public record Email(boolean enabled, String from) {
    }

    /**
     * {@code provider}: {@code none}, {@code log} (development: logs a masked number only) or
     * {@code sparrow} (Sparrow SMS, Nepali mobile numbers).
     */
    public record Sms(String provider, Sparrow sparrow) {
    }

    public record Sparrow(String url, String token, String from) {
    }

    public boolean emailEnabled() {
        return email != null && email.enabled();
    }

    public boolean smsEnabled() {
        return sms != null && sms.provider() != null && !sms.provider().isBlank()
                && !"none".equalsIgnoreCase(sms.provider());
    }

    public List<Integer> reminderDays() {
        return expiryReminderDays == null || expiryReminderDays.isEmpty() ? List.of(3, 1) : expiryReminderDays;
    }

    public int attempts() {
        return maxAttempts > 0 ? maxAttempts : 5;
    }
}
