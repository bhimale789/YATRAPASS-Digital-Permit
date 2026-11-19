package com.example.touristpermit.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/**
 * Strongly-typed application configuration bound from {@code app.*}. All secrets come
 * from environment variables; startup fails fast when a required value is missing.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @NotBlank String baseUrl,
        @Valid @NotNull Security security,
        @Valid @NotNull Storage storage,
        @Valid @NotNull Qr qr,
        @Valid @NotNull Verification verification,
        @Valid @NotNull Permits permits,
        Seed seed,
        @Valid @NotNull Routing routing
) {

    /** Automatic driving routes (OSRM-compatible service) from a default start point. */
    public record Routing(@NotBlank String osrmUrl,
                          @NotNull Duration timeout,
                          @NotNull Duration cacheTtl,
                          @NotBlank String originLabel,
                          @NotNull Double originLatitude,
                          @NotNull Double originLongitude) {
    }

    public record Security(@Valid @NotNull Jwt jwt,
                           List<String> corsAllowedOrigins,
                           @Valid @NotNull LoginRateLimit loginRateLimit) {
    }

    public record Jwt(@NotBlank String secret,
                      @NotNull Duration accessTokenTtl,
                      @NotNull Duration refreshTokenTtl,
                      @NotBlank String issuer) {
    }

    public record LoginRateLimit(@Positive int maxAttempts, @NotNull Duration window) {
    }

    public record Storage(@NotBlank String path,
                          @NotNull List<String> allowedMimeTypes,
                          @NotNull List<String> allowedExtensions,
                          @NotNull DataSize maxFileSize,
                          @Positive int maxDocumentsPerApplication) {
    }

    public record Qr(@NotBlank String encryptionKey) {
    }

    public record Verification(boolean locationEnabled) {
    }

    public record Permits(boolean allowOverlapping, @NotBlank String expiryCron) {
    }

    public record Seed(String adminEmail, String adminPassword, String officerEmail, String officerPassword) {
    }
}
