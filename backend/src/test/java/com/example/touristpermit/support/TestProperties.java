package com.example.touristpermit.support;

import com.example.touristpermit.config.AppProperties;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.Base64;
import java.util.List;

/** AppProperties fixtures for plain unit tests (no Spring context). */
public final class TestProperties {

    public static final String JWT_SECRET =
            Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());
    public static final String QR_KEY =
            Base64.getEncoder().encodeToString("abcdef0123456789abcdef0123456789".getBytes());

    public static final AppProperties.Routing ROUTING = new AppProperties.Routing("http://osrm.test",
            Duration.ofSeconds(5), Duration.ofHours(1), "Kathmandu", 27.7172, 85.3240);

    private TestProperties() {
    }

    public static AppProperties defaults() {
        return withJwtSecret(JWT_SECRET);
    }

    public static AppProperties withJwtSecret(String secret) {
        return build(secret, DataSize.ofMegabytes(5));
    }

    public static AppProperties withMaxUpload(DataSize maxUpload) {
        return build(JWT_SECRET, maxUpload);
    }

    private static AppProperties build(String jwtSecret, DataSize maxUpload) {
        return new AppProperties("http://test.local",
                new AppProperties.Security(
                        new AppProperties.Jwt(jwtSecret, Duration.ofMinutes(15), Duration.ofDays(7), "test"),
                        List.of(), new AppProperties.LoginRateLimit(5, Duration.ofMinutes(5))),
                new AppProperties.Storage("/tmp", List.of("application/pdf", "image/jpeg", "image/png"),
                        List.of("pdf", "jpg", "jpeg", "png"), maxUpload, 10),
                new AppProperties.Qr(QR_KEY), new AppProperties.Verification(false),
                new AppProperties.Permits(false, "0 0 0 * * *"), null, ROUTING);
    }
}
