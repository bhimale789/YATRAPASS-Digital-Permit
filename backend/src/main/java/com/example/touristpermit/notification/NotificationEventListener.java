package com.example.touristpermit.notification;

import com.example.touristpermit.application.PermitApplication;
import com.example.touristpermit.application.PermitApplicationRepository;
import com.example.touristpermit.notification.event.ApplicationDecidedEvent;
import com.example.touristpermit.notification.event.PaymentCompletedEvent;
import com.example.touristpermit.payment.Payment;
import com.example.touristpermit.payment.PaymentProvider;
import com.example.touristpermit.payment.PaymentRepository;
import com.example.touristpermit.permit.Permit;
import com.example.touristpermit.permit.PermitRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Turns domain events into tourist notifications. Runs synchronously inside the
 * publishing transaction (transactional outbox): if the approval rolls back, so does the
 * notification. Messages never contain passport or other identity data.
 */
@Component
public class NotificationEventListener {

    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private final NotificationService notifications;
    private final PermitApplicationRepository applications;
    private final PermitRepository permits;
    private final PaymentRepository payments;

    public NotificationEventListener(NotificationService notifications, PermitApplicationRepository applications,
                                     PermitRepository permits, PaymentRepository payments) {
        this.notifications = notifications;
        this.applications = applications;
        this.permits = permits;
        this.payments = payments;
    }

    @EventListener
    public void onApplicationDecided(ApplicationDecidedEvent event) {
        PermitApplication app = applications.findById(event.applicationId()).orElseThrow();
        Long userId = app.getTourist().getUser().getId();
        String area = app.getArea().getName();
        if (event.approved()) {
            Permit permit = permits.findByApplicationId(app.getId()).orElseThrow();
            notifications.notify(userId, NotificationType.APPLICATION_APPROVED,
                    "Permit approved: " + area,
                    "Your application %s for %s has been approved. Permit %s is valid from %s to %s. "
                            .formatted(app.getApplicationNumber(), area, permit.getPermitNumber(),
                                    format(permit.getValidFrom()), format(permit.getValidUntil()))
                            + "Open YatraPass to view or download your permit and QR code.",
                    "/tourist/permits", "APPLICATION_APPROVED:" + app.getId());
        } else {
            notifications.notify(userId, NotificationType.APPLICATION_REJECTED,
                    "Application not approved: " + area,
                    "Your application %s for %s was not approved. Reason: %s"
                            .formatted(app.getApplicationNumber(), area, app.getRejectionReason()),
                    "/tourist/applications/" + app.getId(), "APPLICATION_REJECTED:" + app.getId());
        }
    }

    @EventListener
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        Payment payment = payments.findById(event.paymentId()).orElseThrow();
        PermitApplication app = payment.getApplication();
        String via = payment.getProvider() == PaymentProvider.ESEWA ? "eSewa" : "the permit office";
        notifications.notify(app.getTourist().getUser().getId(), NotificationType.PAYMENT_RECEIVED,
                "Payment received",
                "We received NPR %s via %s for application %s (%s). Reference: %s."
                        .formatted(payment.getAmount().stripTrailingZeros().toPlainString(), via,
                                app.getApplicationNumber(), app.getArea().getName(), payment.getProviderRef()),
                "/tourist/applications/" + app.getId(), "PAYMENT_RECEIVED:" + payment.getId());
    }

    static String format(LocalDate date) {
        return DATE.format(date);
    }
}
