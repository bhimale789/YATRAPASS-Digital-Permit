package com.example.touristpermit.notification;

import com.example.touristpermit.permit.Permit;
import com.example.touristpermit.permit.PermitRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;

/**
 * Daily reminder for ACTIVE permits that expire in N days (default 3 and 1). The dedup key
 * makes re-runs harmless; ShedLock keeps it to one instance.
 */
@Component
public class PermitExpiryReminderJob {

    private static final Logger log = LoggerFactory.getLogger(PermitExpiryReminderJob.class);

    private final PermitRepository permits;
    private final NotificationService notifications;
    private final NotificationProperties properties;
    private final Clock clock;

    public PermitExpiryReminderJob(PermitRepository permits, NotificationService notifications,
                                   NotificationProperties properties, Clock clock) {
        this.permits = permits;
        this.notifications = notifications;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.notifications.expiry-reminder-cron:0 0 8 * * *}", zone = "Asia/Kathmandu")
    @SchedulerLock(name = "permitExpiryReminders")
    public void runScheduled() {
        int sent = run();
        if (sent > 0) {
            log.info("Queued {} permit expiry reminders", sent);
        }
    }

    @Transactional
    public int run() {
        List<Integer> days = properties.reminderDays();
        LocalDate today = LocalDate.now(clock);
        LocalDate until = today.plusDays(Collections.max(days));
        int sent = 0;
        int page = 0;
        List<Permit> batch;
        do {
            batch = permits.findExpiringBetween(today, until, PageRequest.of(page++, 200)).getContent();
            for (Permit p : batch) {
                int left = (int) ChronoUnit.DAYS.between(today, p.getValidUntil());
                if (days.contains(left) && remind(p, left)) {
                    sent++;
                }
            }
        } while (batch.size() == 200);
        return sent;
    }

    private boolean remind(Permit p, int daysLeft) {
        String when = daysLeft == 0 ? "today" : daysLeft == 1 ? "tomorrow" : "in " + daysLeft + " days";
        return notifications.notify(p.getTourist().getUser().getId(), NotificationType.PERMIT_EXPIRING,
                "Permit expires " + when,
                "Your permit %s for %s is valid until %s. Plan to leave the area by then, or apply for a new permit."
                        .formatted(p.getPermitNumber(), p.getArea().getName(),
                                NotificationEventListener.format(p.getValidUntil())),
                "/tourist/permits", "PERMIT_EXPIRING:" + p.getId() + ":" + daysLeft);
    }
}
