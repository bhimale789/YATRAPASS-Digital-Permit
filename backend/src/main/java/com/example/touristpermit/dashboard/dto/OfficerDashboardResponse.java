package com.example.touristpermit.dashboard.dto;

import com.example.touristpermit.verification.VerificationReason;
import com.example.touristpermit.verification.VerificationResult;

import java.time.Instant;
import java.util.List;

/**
 * Officer's own activity. Contains only the officer's scans and aggregate counts —
 * no other officers' activity and no tourist personal data.
 */
public record OfficerDashboardResponse(Stats stats, List<RecentScan> recentScans) {

    public record Stats(
            long scansToday,
            long validToday,
            long invalidToday,
            long totalScans,
            long permitsValidToday
    ) {
    }

    public record RecentScan(
            Long id,
            Instant verifiedAt,
            VerificationResult result,
            VerificationReason failureReason,
            String permitNumber,
            String area
    ) {
    }
}
