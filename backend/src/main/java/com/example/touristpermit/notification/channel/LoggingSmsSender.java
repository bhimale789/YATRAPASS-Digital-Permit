package com.example.touristpermit.notification.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Development SMS "provider": records that a message would have been sent. Logs only a
 * masked number and the message length, never the text (it may contain permit details).
 */
@Component
@ConditionalOnProperty(name = "app.notifications.sms.provider", havingValue = "log")
public class LoggingSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingSmsSender.class);

    @Override
    public void send(String phoneNumber, String text) {
        log.info("[dev SMS] to {} ({} characters)", SmsSender.mask(phoneNumber), text.length());
    }
}
