package com.example.touristpermit.verification.dto;

import com.example.touristpermit.verification.PermitVerification;
import com.example.touristpermit.verification.VerificationReason;
import com.example.touristpermit.verification.VerificationResult;

import java.math.BigDecimal;
import java.time.Instant;

public record VerificationLogResponse(
        Long id,
        Instant verifiedAt,
        VerificationResult result,
        VerificationReason failureReason,
        Long permitId,
        String permitNumber,
        String area,
        Long verifiedById,
        String verifiedByEmail,
        String ipAddress,
        String userAgent,
        BigDecimal latitude,
        BigDecimal longitude
) {

    public static VerificationLogResponse from(PermitVerification v) {
        var p = v.getPermit();
        return new VerificationLogResponse(v.getId(), v.getVerifiedAt(), v.getResult(), v.getFailureReason(),
                p == null ? null : p.getId(), p == null ? null : p.getPermitNumber(),
                p == null ? null : p.getArea().getName(),
                v.getVerifiedBy().getId(), v.getVerifiedBy().getEmail(), v.getIpAddress(), v.getUserAgent(),
                v.getLatitude(), v.getLongitude());
    }
}
