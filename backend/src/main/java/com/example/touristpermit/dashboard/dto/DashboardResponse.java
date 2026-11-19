package com.example.touristpermit.dashboard.dto;

import com.example.touristpermit.application.dto.ApplicationResponse;
import com.example.touristpermit.permit.dto.AreaPermitStats;
import com.example.touristpermit.permit.dto.PermitResponse;
import com.example.touristpermit.verification.dto.VerificationLogResponse;

import java.math.BigDecimal;
import java.util.List;

public record DashboardResponse(
        Stats stats,
        List<ApplicationResponse> recentApplications,
        List<VerificationLogResponse> recentVerifications,
        List<PermitResponse> expiringPermits,
        List<AreaPermitStats> permitsByArea,
        Fees fees
) {

    public record Fees(
            BigDecimal collectedTotal,
            BigDecimal collectedThisMonth,
            long unpaidOpenApplications,
            boolean onlinePaymentsEnabled
    ) {
    }

    public record Stats(
            long totalTourists,
            long pendingApplications,
            long underReviewApplications,
            long activePermits,
            long expiredPermits,
            long cancelledPermits,
            long completedPermits,
            long verificationsToday,
            long validScansToday,
            long invalidScansToday
    ) {
    }
}
