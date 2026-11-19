package com.example.touristpermit.payment;

import java.util.Map;

/** eSewa ePay v2 integration points (signing and server-to-server status checks). */
public interface EsewaGateway {

    /** eSewa transaction status as reported by its status API. */
    record Status(String status, String refId) {
        public boolean isComplete() {
            return "COMPLETE".equals(status);
        }

        /** eSewa has no record, or the user abandoned / cancelled the payment. */
        public boolean isAbandoned() {
            return "NOT_FOUND".equals(status) || "CANCELED".equals(status);
        }
    }

    /** HMAC-SHA256 (base64) over {@code name=value,...} for the given signed field names. */
    String sign(Map<String, String> fields, String signedFieldNames);

    /**
     * Queries eSewa for the transaction. Throws
     * {@link com.example.touristpermit.common.exception.ApiException} (503) if eSewa is unreachable.
     */
    Status checkStatus(String totalAmount, String transactionUuid);
}
