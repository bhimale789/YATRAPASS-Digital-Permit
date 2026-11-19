package com.example.touristpermit.permit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Moves ACTIVE permits past their validity to EXPIRED daily and once at startup
 * (to catch up after downtime). Verification additionally checks dates directly,
 * so a permit is never reported valid after its end date even before this job runs.
 */
@Component
public class PermitExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(PermitExpiryJob.class);

    private final PermitService permitService;

    public PermitExpiryJob(PermitService permitService) {
        this.permitService = permitService;
    }

    @Scheduled(cron = "${app.permits.expiry-cron}", zone = "Asia/Kathmandu")
    @SchedulerLock(name = "permitExpiry")
    public void runScheduled() {
        run();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void runAtStartup() {
        run();
    }

    private void run() {
        try {
            int expired = permitService.expireDuePermits();
            if (expired > 0) {
                log.info("Expired {} permits", expired);
            }
        } catch (RuntimeException e) {
            log.error("Permit expiry job failed", e);
        }
    }
}
