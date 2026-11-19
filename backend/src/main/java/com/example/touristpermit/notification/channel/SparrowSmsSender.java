package com.example.touristpermit.notification.channel;

import com.example.touristpermit.notification.NotificationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.regex.Pattern;

/**
 * Sparrow SMS (Nepal). Only Nepali mobile numbers (98XXXXXXXX / 97XXXXXXXX, optionally
 * prefixed with +977) can be served; other numbers are skipped, not retried.
 */
@Component
@ConditionalOnProperty(name = "app.notifications.sms.provider", havingValue = "sparrow")
public class SparrowSmsSender implements SmsSender {

    private static final Pattern NEPAL_MOBILE = Pattern.compile("9[678]\\d{8}");
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final NotificationProperties.Sparrow config;
    private final RestClient client;

    public SparrowSmsSender(NotificationProperties properties, RestClient.Builder builder) {
        this.config = properties.sms().sparrow();
        if (config == null || config.token() == null || config.token().isBlank()) {
            throw new IllegalStateException("SMS_PROVIDER=sparrow requires SPARROW_SMS_TOKEN");
        }
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
        factory.setReadTimeout(TIMEOUT);
        this.client = builder.requestFactory(factory).build();
    }

    @Override
    public void send(String phoneNumber, String text) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("token", config.token());
        form.add("from", config.from());
        form.add("to", toLocalNumber(phoneNumber));
        form.add("text", text);
        // Non-2xx responses throw and the delivery is retried with backoff.
        client.post().uri(config.url()).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
                .retrieve().toBodilessEntity();
    }

    static String toLocalNumber(String phoneNumber) {
        String digits = phoneNumber == null ? "" : phoneNumber.replaceAll("\\D", "");
        if (digits.startsWith("977") && digits.length() == 13) {
            digits = digits.substring(3);
        }
        if (!NEPAL_MOBILE.matcher(digits).matches()) {
            throw new RecipientNotSupportedException("Not a Nepali mobile number");
        }
        return digits;
    }
}
