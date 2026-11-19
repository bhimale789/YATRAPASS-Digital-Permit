package com.example.touristpermit.payment.dto;

import com.example.touristpermit.payment.Payment;
import com.example.touristpermit.payment.PaymentProvider;
import com.example.touristpermit.payment.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public final class PaymentDtos {

    private PaymentDtos() {
    }

    /** Fee/payment state of an application, as shown to tourists and administrators. */
    public enum FeeStatus {
        /** The application has no fee. */
        NOT_REQUIRED,
        UNPAID,
        /** A payment was started and is awaiting confirmation from the gateway. */
        PENDING,
        PAID
    }

    public record PaymentSummary(
            FeeStatus status,
            BigDecimal amount,
            String currency,
            PaymentProvider provider,
            String reference,
            Instant paidAt,
            @Schema(description = "True when the fee must be paid before the application can be approved")
            boolean requiredForApproval
    ) {
    }

    public record PaymentResponse(
            Long id,
            String transactionUuid,
            PaymentProvider provider,
            BigDecimal amount,
            String currency,
            PaymentStatus status,
            String reference,
            String failureReason,
            Instant createdAt,
            Instant completedAt,
            @Schema(description = "Administrator views") String applicationNumber,
            @Schema(description = "Administrator views") Long applicationId,
            @Schema(description = "Administrator views") String touristName
    ) {
        public static PaymentResponse of(Payment p, boolean adminView) {
            return new PaymentResponse(p.getId(), p.getTransactionUuid(), p.getProvider(), p.getAmount(), p.getCurrency(),
                    p.getStatus(), p.getProviderRef(), p.getFailureReason(), p.getCreatedAt(), p.getCompletedAt(),
                    adminView ? p.getApplication().getApplicationNumber() : null,
                    p.getApplication().getId(),
                    adminView ? p.getApplication().getTourist().getFullName() : null);
        }
    }

    @Schema(description = "Browser form POST to eSewa: submit every field to formUrl")
    public record EsewaCheckout(String formUrl, Map<String, String> fields, String transactionUuid) {
    }

    public record EsewaConfirmRequest(
            @Schema(description = "The base64 'data' query parameter eSewa appends to the success URL")
            @NotBlank @Size(max = 4000) String data
    ) {
    }

    public record OfflinePaymentRequest(
            @Schema(example = "Receipt 2026/11/0042") @NotBlank @Size(max = 100) String reference
    ) {
    }
}
