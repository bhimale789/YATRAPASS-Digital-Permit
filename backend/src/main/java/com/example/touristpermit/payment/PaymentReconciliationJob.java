package com.example.touristpermit.payment;

import com.example.touristpermit.common.exception.ApiException;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Settles eSewa payments the tourist never returned from (closed the tab, lost
 * connection). Asks eSewa for each stale PENDING payment; payments still unresolved after
 * a day are marked FAILED. Runs on one instance at a time.
 */
@Component
public class PaymentReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationJob.class);
    static final Duration STALE_AFTER = Duration.ofMinutes(15);
    static final Duration GIVE_UP_AFTER = Duration.ofDays(1);

    private final PaymentRepository payments;
    private final PaymentService paymentService;
    private final PaymentProperties properties;
    private final TransactionTemplate tx;
    private final Clock clock;

    public PaymentReconciliationJob(PaymentRepository payments, PaymentService paymentService,
                                    PaymentProperties properties, PlatformTransactionManager txManager, Clock clock) {
        this.payments = payments;
        this.paymentService = paymentService;
        this.properties = properties;
        this.tx = new TransactionTemplate(txManager);
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.payments.reconcile-interval:PT10M}",
            initialDelayString = "${app.payments.reconcile-initial-delay:PT2M}")
    @SchedulerLock(name = "paymentReconciliation")
    public void run() {
        if (!properties.esewaEnabled()) {
            return;
        }
        Instant now = clock.instant();
        int settled = 0;
        for (String uuid : payments.findStalePendingEsewa(now.minus(STALE_AFTER))) {
            try {
                Boolean done = tx.execute(status -> payments.findByTransactionUuidForUpdate(uuid).map(p -> {
                    paymentService.settle(p);
                    if (p.getStatus() == PaymentStatus.PENDING && p.getCreatedAt().isBefore(now.minus(GIVE_UP_AFTER))) {
                        p.close(PaymentStatus.FAILED, "Not confirmed by eSewa within a day");
                    }
                    return p.getStatus() != PaymentStatus.PENDING;
                }).orElse(false));
                if (Boolean.TRUE.equals(done)) {
                    settled++;
                }
            } catch (ApiException e) {
                log.warn("Payment reconciliation paused: eSewa unavailable");
                return;
            }
        }
        if (settled > 0) {
            log.info("Reconciled {} pending payments", settled);
        }
    }
}
