package com.example.touristpermit.payment;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EsewaSignatureTest {

    private final HttpEsewaGateway gateway = new HttpEsewaGateway(
            new PaymentProperties(new PaymentProperties.Esewa(true, "EPAYTEST", "8gBm/:&EnhH.1/q", "f", "s")),
            RestClient.builder());

    @Test
    void matchesEsewaDocumentationExample() {
        // Known answer (eSewa ePay v2 docs example; cross-checked with Python's hmac module)
        String signature = gateway.sign(Map.of("total_amount", "110", "transaction_uuid", "241028",
                "product_code", "EPAYTEST"), "total_amount,transaction_uuid,product_code");
        assertThat(signature).isEqualTo("i94zsd3oXF6ZsSr/kGqT4sSzYQzjj1W/waxjWyRwaME=");
    }

    @Test
    void signsFieldsInTheGivenOrder() {
        Map<String, String> fields = Map.of("a", "1", "b", "2");
        assertThat(gateway.sign(fields, "a,b")).isNotEqualTo(gateway.sign(fields, "b,a"));
        assertThatThrownBy(() -> gateway.sign(fields, "a,c")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void amountsAreFormattedAndParsedLikeEsewa() {
        assertThat(PaymentService.format(new BigDecimal("1500.00"))).isEqualTo("1500");
        assertThat(PaymentService.format(new BigDecimal("10.50"))).isEqualTo("10.5");
        assertThat(PaymentService.parseAmount("1,000.0")).isEqualByComparingTo("1000");
    }
}
