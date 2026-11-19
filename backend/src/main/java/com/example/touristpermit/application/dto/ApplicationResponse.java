package com.example.touristpermit.application.dto;

import com.example.touristpermit.application.ApplicationStatus;
import com.example.touristpermit.application.PermitApplication;
import com.example.touristpermit.area.dto.AreaSummary;
import com.example.touristpermit.document.dto.DocumentResponse;
import com.example.touristpermit.payment.dto.PaymentDtos.PaymentSummary;
import com.example.touristpermit.tourist.dto.TouristSummaryResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ApplicationResponse(
        Long id,
        @Schema(example = "APP-2026-000001") String applicationNumber,
        AreaSummary area,
        String purpose,
        LocalDate startDate,
        LocalDate endDate,
        long requestedDays,
        ApplicationStatus status,
        String rejectionReason,
        Instant submittedAt,
        Instant reviewedAt,
        @Schema(description = "Present in detail views") List<DocumentResponse> documents,
        @Schema(description = "Present in administrator views") TouristSummaryResponse tourist,
        @Schema(description = "Issued permit (when APPROVED)") IssuedPermitRef permit,
        @Schema(description = "Permit fee fixed when the application was submitted (NPR)") BigDecimal feeAmount,
        @Schema(description = "Fee payment status") PaymentSummary payment
) {

    public record IssuedPermitRef(Long id, String permitNumber) {
    }

    public static ApplicationResponse summary(PermitApplication a) {
        return of(a, null, null, null);
    }

    public static ApplicationResponse of(PermitApplication a, List<DocumentResponse> documents,
                                         TouristSummaryResponse tourist, IssuedPermitRef permit) {
        return new ApplicationResponse(a.getId(), a.getApplicationNumber(), AreaSummary.from(a.getArea()),
                a.getPurpose(), a.getRequestedStartDate(), a.getRequestedEndDate(), a.requestedDays(), a.getStatus(),
                a.getRejectionReason(), a.getSubmittedAt(), a.getReviewedAt(), documents, tourist, permit,
                a.getFeeAmount(), null);
    }

    public ApplicationResponse withPayment(PaymentSummary summary) {
        return new ApplicationResponse(id, applicationNumber, area, purpose, startDate, endDate, requestedDays, status,
                rejectionReason, submittedAt, reviewedAt, documents, tourist, permit, feeAmount, summary);
    }
}
