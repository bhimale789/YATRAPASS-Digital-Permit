package com.example.touristpermit.verification.dto;

import com.example.touristpermit.permit.PermitStatus;
import com.example.touristpermit.verification.VerificationReason;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Safe verification result. Contains only what an officer needs at a checkpoint —
 * never passport number, date of birth, contact details or address. Tourist identity
 * fields are included only for VALID results.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "QR verification outcome", example = """
        {"valid":true,"status":"ACTIVE","permitNumber":"PRM-2026-000001","touristName":"John Smith",
         "nationality":"USA","area":"Sample Restricted Area","validFrom":"2026-10-10","validUntil":"2026-10-15",
         "verifiedAt":"2026-10-11T04:15:00Z"}""")
public record PermitVerificationResponse(
        boolean valid,
        @Schema(description = "Present when valid=false") VerificationReason reason,
        @Schema(description = "Human-readable reason when valid=false") String message,
        PermitStatus status,
        String permitNumber,
        String touristName,
        String nationality,
        String area,
        String areaCode,
        LocalDate validFrom,
        LocalDate validUntil,
        Instant verifiedAt
) {

    public static PermitVerificationResponse invalid(VerificationReason reason, Instant at) {
        return new PermitVerificationResponse(false, reason, reason.message(), null, null, null, null, null, null,
                null, null, at);
    }
}
