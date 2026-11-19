package com.example.touristpermit.permit.dto;

import com.example.touristpermit.area.dto.AreaSummary;
import com.example.touristpermit.permit.Permit;
import com.example.touristpermit.permit.PermitStatus;
import com.example.touristpermit.tourist.dto.TouristSummaryResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

public record PermitResponse(
        Long id,
        @Schema(example = "PRM-2026-000001") String permitNumber,
        Long applicationId,
        String applicationNumber,
        AreaSummary area,
        LocalDate issueDate,
        LocalDate validFrom,
        LocalDate validUntil,
        PermitStatus status,
        String cancellationReason,
        @Schema(description = "True when the permit has an active (non-revoked) QR code") boolean qrActive,
        @Schema(description = "Present in administrator views") TouristSummaryResponse tourist
) {

    public static PermitResponse of(Permit p, boolean qrActive, TouristSummaryResponse tourist) {
        return new PermitResponse(p.getId(), p.getPermitNumber(), p.getApplication().getId(),
                p.getApplication().getApplicationNumber(), AreaSummary.from(p.getArea()), p.getIssueDate(),
                p.getValidFrom(), p.getValidUntil(), p.getStatus(), p.getCancellationReason(), qrActive, tourist);
    }
}
