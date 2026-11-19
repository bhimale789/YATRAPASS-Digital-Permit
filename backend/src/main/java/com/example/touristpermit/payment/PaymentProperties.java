package com.example.touristpermit.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Online fee payment settings. When eSewa is disabled, fees are not collected online
 * and approval does not wait for a payment (administrators may record counter payments).
 */
@ConfigurationProperties(prefix = "app.payments")
public record PaymentProperties(Esewa esewa) {

    /**
     * eSewa ePay v2. The UAT (test) environment uses product code {@code EPAYTEST} and the
     * public test secret from eSewa's developer documentation; production values come
     * from the merchant agreement and must be supplied via environment variables.
     */
    public record Esewa(boolean enabled, String productCode, String secretKey, String formUrl, String statusUrl) {
    }

    public boolean esewaEnabled() {
        return esewa != null && esewa.enabled();
    }
}
