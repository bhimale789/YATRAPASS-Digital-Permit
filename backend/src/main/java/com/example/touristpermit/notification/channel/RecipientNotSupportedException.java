package com.example.touristpermit.notification.channel;

/** Permanent failure: retrying will not help (e.g. a foreign number for a Nepal-only SMS provider). */
public class RecipientNotSupportedException extends RuntimeException {

    public RecipientNotSupportedException(String message) {
        super(message);
    }
}
