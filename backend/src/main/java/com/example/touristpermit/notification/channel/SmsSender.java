package com.example.touristpermit.notification.channel;

/** Sends a text message. Implementations are selected by {@code app.notifications.sms.provider}. */
public interface SmsSender {

    /**
     * @throws RecipientNotSupportedException if the number cannot be served by this provider
     * @throws RuntimeException on a temporary failure (the delivery is retried)
     */
    void send(String phoneNumber, String text);

    /** Shows only the last three digits, for logs. */
    static String mask(String phoneNumber) {
        String digits = phoneNumber == null ? "" : phoneNumber.replaceAll("\\D", "");
        return digits.length() <= 3 ? "***" : "*".repeat(4) + digits.substring(digits.length() - 3);
    }
}
