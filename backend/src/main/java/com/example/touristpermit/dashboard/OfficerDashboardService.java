package com.example.touristpermit.dashboard;

import com.example.touristpermit.dashboard.dto.OfficerDashboardResponse;
import com.example.touristpermit.permit.PermitRepository;
import com.example.touristpermit.security.CurrentUser;
import com.example.touristpermit.verification.PermitVerificationRepository;
import com.example.touristpermit.verification.VerificationResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

@Service
public class OfficerDashboardService {

    private final PermitVerificationRepository verifications;
    private final PermitRepository permits;
    private final Clock clock;

    public OfficerDashboardService(PermitVerificationRepository verifications, PermitRepository permits, Clock clock) {
        this.verifications = verifications;
        this.permits = permits;
        this.clock = clock;
    }

    /** Dashboard for the currently authenticated officer, scoped to their own scans. */
    @Transactional(readOnly = true)
    public OfficerDashboardResponse dashboard() {
        Long officerId = CurrentUser.require().id();
        LocalDate today = LocalDate.now(clock);
        Instant startOfDay = today.atStartOfDay(clock.getZone()).toInstant();

        long scansToday = verifications.countByVerifiedByIdAndVerifiedAtGreaterThanEqual(officerId, startOfDay);
        long validToday = verifications.countByVerifiedByIdAndResultAndVerifiedAtGreaterThanEqual(
                officerId, VerificationResult.VALID, startOfDay);

        var stats = new OfficerDashboardResponse.Stats(scansToday, validToday, scansToday - validToday,
                verifications.countByVerifiedById(officerId), permits.countValidOn(today));

        var recent = verifications.findTop15ByVerifiedByIdOrderByVerifiedAtDesc(officerId).stream()
                .map(v -> new OfficerDashboardResponse.RecentScan(v.getId(), v.getVerifiedAt(), v.getResult(),
                        v.getFailureReason(),
                        v.getPermit() == null ? null : v.getPermit().getPermitNumber(),
                        v.getPermit() == null ? null : v.getPermit().getArea().getName()))
                .toList();

        return new OfficerDashboardResponse(stats, recent);
    }
}
