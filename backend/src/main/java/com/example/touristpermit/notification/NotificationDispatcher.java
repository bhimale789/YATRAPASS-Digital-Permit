package com.example.touristpermit.notification;

import com.example.touristpermit.config.AppProperties;
import com.example.touristpermit.notification.channel.RecipientNotSupportedException;
import com.example.touristpermit.notification.channel.SmsSender;
import com.example.touristpermit.tourist.TouristProfile;
import com.example.touristpermit.tourist.TouristProfileRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.List;

/**
 * Sends queued email/SMS deliveries. Failures are retried with exponential backoff;
 * permanent problems (no phone number, unsupported number, channel not configured) are
 * marked SKIPPED. Each delivery is processed in its own transaction with a row lock.
 */
@Component
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);
    private static final int BATCH = 50;

    private final NotificationDeliveryRepository deliveries;
    private final TouristProfileRepository profiles;
    private final ObjectProvider<JavaMailSender> mailSender;
    private final ObjectProvider<SmsSender> smsSender;
    private final NotificationProperties properties;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final String baseUrl;

    public NotificationDispatcher(NotificationDeliveryRepository deliveries, TouristProfileRepository profiles,
                                  ObjectProvider<JavaMailSender> mailSender, ObjectProvider<SmsSender> smsSender,
                                  NotificationProperties properties, PlatformTransactionManager txManager,
                                  Clock clock, AppProperties appProperties) {
        this.deliveries = deliveries;
        this.profiles = profiles;
        this.mailSender = mailSender;
        this.smsSender = smsSender;
        this.properties = properties;
        this.tx = new TransactionTemplate(txManager);
        this.clock = clock;
        this.baseUrl = appProperties.baseUrl().replaceAll("/+$", "");
    }

    @Scheduled(fixedDelayString = "${app.notifications.dispatch-interval:PT15S}",
            initialDelayString = "${app.notifications.dispatch-initial-delay:PT30S}")
    @SchedulerLock(name = "notificationDispatch", lockAtMostFor = "PT5M")
    public void runScheduled() {
        dispatch();
    }

    /** Sends everything that is due. Returns the number of deliveries processed. */
    public int dispatch() {
        int processed = 0;
        List<Long> due;
        do {
            due = deliveries.findDueIds(clock.instant(), BATCH);
            for (Long id : due) {
                tx.executeWithoutResult(status -> deliveries.findForUpdate(id)
                        .filter(d -> d.getStatus() == DeliveryStatus.PENDING)
                        .ifPresent(this::deliver));
                processed++;
            }
        } while (due.size() == BATCH);
        return processed;
    }

    private void deliver(NotificationDelivery d) {
        Notification n = d.getNotification();
        try {
            switch (d.getChannel()) {
                case EMAIL -> sendEmail(n);
                case SMS -> sendSms(n);
            }
            d.markSent(clock.instant());
        } catch (RecipientNotSupportedException e) {
            d.markSkipped(e.getMessage());
        } catch (RuntimeException e) {
            // Exception messages from mail/HTTP clients may echo addresses; store the type only.
            d.markAttemptFailed(e.getClass().getSimpleName(), clock.instant(), properties.attempts());
            log.warn("{} delivery {} failed (attempt {}): {}", d.getChannel(), d.getId(), d.getAttempts(),
                    e.getClass().getSimpleName());
        }
    }

    private void sendEmail(Notification n) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            throw new RecipientNotSupportedException("Mail server not configured (SPRING_MAIL_HOST)");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.email().from());
        message.setTo(n.getUser().getEmail());
        message.setSubject("YatraPass: " + n.getTitle());
        message.setText(n.getBody()
                + (n.getLink() == null ? "" : "\n\nOpen YatraPass: " + baseUrl + n.getLink())
                + "\n\n--\nThis is an automated message from YatraPass. Please do not reply.");
        sender.send(message);
    }

    private void sendSms(Notification n) {
        SmsSender sender = smsSender.getIfAvailable();
        if (sender == null) {
            throw new RecipientNotSupportedException("SMS provider not configured");
        }
        String phone = profiles.findByUserId(n.getUser().getId()).map(TouristProfile::getPhone)
                .filter(p -> !p.isBlank())
                .orElseThrow(() -> new RecipientNotSupportedException("No phone number on profile"));
        String text = "YatraPass: " + n.getTitle() + ". " + n.getBody();
        sender.send(phone, text.length() > 459 ? text.substring(0, 456) + "..." : text);
    }
}
