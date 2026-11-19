package com.example.touristpermit.payment;

import com.example.touristpermit.common.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.StringJoiner;

/**
 * eSewa ePay v2: HMAC-SHA256 request/response signatures and the transaction status API.
 * The secret key never leaves the backend.
 */
@Component
public class HttpEsewaGateway implements EsewaGateway {

    private static final Logger log = LoggerFactory.getLogger(HttpEsewaGateway.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final PaymentProperties properties;
    private final RestClient client;

    public HttpEsewaGateway(PaymentProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).followRedirects(HttpClient.Redirect.NORMAL).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(TIMEOUT);
        this.client = builder.requestFactory(factory).build();
    }

    @Override
    public String sign(Map<String, String> fields, String signedFieldNames) {
        StringJoiner message = new StringJoiner(",");
        for (String name : signedFieldNames.split(",")) {
            String value = fields.get(name.trim());
            if (value == null) {
                throw new IllegalArgumentException("Missing signed field " + name);
            }
            message.add(name.trim() + "=" + value);
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(esewa().secretKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(message.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    @Override
    public Status checkStatus(String totalAmount, String transactionUuid) {
        String uri = UriComponentsBuilder.fromUriString(esewa().statusUrl())
                .queryParam("product_code", esewa().productCode())
                .queryParam("total_amount", totalAmount)
                .queryParam("transaction_uuid", transactionUuid)
                .toUriString();
        try {
            JsonNode body = client.get().uri(uri).retrieve().body(JsonNode.class);
            if (body == null || !body.hasNonNull("status")) {
                throw unavailable();
            }
            return new Status(body.get("status").asText(), body.path("ref_id").asText(null));
        } catch (RestClientException e) {
            log.warn("eSewa status check failed: {}", e.getClass().getSimpleName());
            throw unavailable();
        }
    }

    private PaymentProperties.Esewa esewa() {
        return properties.esewa();
    }

    private static ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                "Could not confirm the payment with eSewa right now. Please try again shortly.");
    }
}
