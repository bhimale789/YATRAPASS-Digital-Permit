package com.example.touristpermit.dashboard;

import com.example.touristpermit.application.ApplicationStatus;
import com.example.touristpermit.application.PermitApplicationRepository;
import com.example.touristpermit.application.dto.ApplicationResponse;
import com.example.touristpermit.dashboard.dto.DashboardResponse;
import com.example.touristpermit.permit.PermitRepository;
import com.example.touristpermit.permit.PermitService;
import com.example.touristpermit.permit.PermitStatus;
import com.example.touristpermit.tourist.TouristProfileRepository;
import com.example.touristpermit.tourist.dto.TouristSummaryResponse;
import com.example.touristpermit.verification.PermitVerificationRepository;
import com.example.touristpermit.verification.VerificationResult;
import com.example.touristpermit.verification.dto.VerificationLogResponse;
import org.springframework.data.domain.PageRequest;
import com.example.touristpermit.payment.PaymentProperties;
import com.example.touristpermit.payment.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

/** Aggregates for the admin dashboard. All lists are bounded; nothing unbounded is sent to the browser. */
@Service
public class DashboardService {

    private static final int EXPIRING_WITHIN_DAYS = 7;
    private static final int LIST_SIZE = 10;

    private final TouristProfileRepository tourists;
    private final PermitApplicationRepository applications;
    private final PermitRepository permits;
    private final PermitVerificationRepository verifications;
    private final PermitService permitService;
    private final PaymentRepository payments;
    private final PaymentProperties paymentProperties;
    private final Clock clock;

    public DashboardService(TouristProfileRepository tourists, PermitApplicationRepository applications,
                            PermitRepository permits, PermitVerificationRepository verifications,
                            PermitService permitService, PaymentRepository payments,
                            PaymentProperties paymentProperties, Clock clock) {
        this.tourists = tourists;
        this.applications = applications;
        this.permits = permits;
        this.verifications = verifications;
        this.permitService = permitService;
        this.payments = payments;
        this.paymentProperties = paymentProperties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard() {
        LocalDate today = LocalDate.now(clock);
        Instant startOfDay = today.atStartOfDay(clock.getZone()).toInstant();

        long scansToday = verifications.countByVerifiedAtGreaterThanEqual(startOfDay);
        long validToday = verifications.countByResultAndVerifiedAtGreaterThanEqual(VerificationResult.VALID, startOfDay);

        var stats = new DashboardResponse.Stats(
                tourists.count(),
                applications.countByStatus(ApplicationStatus.PENDING),
                applications.countByStatus(ApplicationStatus.UNDER_REVIEW),
                permits.countByStatus(PermitStatus.ACTIVE),
                permits.countByStatus(PermitStatus.EXPIRED),
                permits.countByStatus(PermitStatus.CANCELLED),
                permits.countByStatus(PermitStatus.COMPLETED),
                scansToday, validToday, scansToday - validToday);

        var recentApplications = applications.findTop10ByOrderBySubmittedAtDesc().stream()
                .map(a -> ApplicationResponse.of(a, null, TouristSummaryResponse.from(a.getTourist()), null))
                .toList();
        var recentVerifications = verifications.findTop10ByOrderByVerifiedAtDesc().stream()
                .map(VerificationLogResponse::from)
                .toList();
        var expiring = permitService.expiringWithin(EXPIRING_WITHIN_DAYS, PageRequest.of(0, LIST_SIZE)).content();
        var byArea = permits.statsByArea(PageRequest.of(0, LIST_SIZE));

        var fees = new DashboardResponse.Fees(payments.totalCollected(),
                payments.collectedSince(today.withDayOfMonth(1).atStartOfDay(clock.getZone()).toInstant()),
                payments.countUnpaidOpenApplications(), paymentProperties.esewaEnabled());

        return new DashboardResponse(stats, recentApplications, recentVerifications, expiring, byArea, fees);
    }
}
