package com.example.touristpermit.verification;

/** Why a QR verification failed, with the result category recorded in the log. */
public enum VerificationReason {
    INVALID_TOKEN("QR code is not a valid permit code.", VerificationResult.INVALID),
    TOKEN_REVOKED("This QR code has been revoked.", VerificationResult.INVALID),
    PERMIT_NOT_FOUND("No permit is associated with this QR code.", VerificationResult.INVALID),
    PERMIT_CANCELLED("Permit has been cancelled.", VerificationResult.CANCELLED),
    PERMIT_EXPIRED("Permit has expired.", VerificationResult.EXPIRED),
    PERMIT_NOT_ACTIVE("Permit is not active.", VerificationResult.INVALID),
    PERMIT_NOT_YET_VALID("Permit is not valid until its start date.", VerificationResult.INVALID),
    AREA_MISMATCH("Permit is not valid for this area.", VerificationResult.INVALID);

    private final String message;
    private final VerificationResult result;

    VerificationReason(String message, VerificationResult result) {
        this.message = message;
        this.result = result;
    }

    public String message() {
        return message;
    }

    public VerificationResult result() {
        return result;
    }
}
