package com.example.touristpermit.area.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AreaRequest(
        @Schema(example = "Sample Restricted Area") @NotBlank @Size(max = 150) String name,
        @Schema(example = "SAMPLE-RA") @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9_-]{2,30}$", message = "must be 2-30 letters, digits, '-' or '_'")
        String code,
        @Size(max = 2000) String description,
        @Size(max = 100) String district,
        @Size(max = 100) String province,
        @NotNull Boolean permitRequired,
        @Schema(example = "10") @NotNull @Min(1) @Max(365) Integer maximumDays,
        @Schema(example = "0.00") @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal fee,
        @NotNull Boolean active,
        @Schema(description = "Destination pin latitude (optional; used for routes)", example = "28.7804")
        @DecimalMin("-90") @DecimalMax("90") @Digits(integer = 3, fraction = 6) BigDecimal latitude,
        @Schema(description = "Destination pin longitude (optional; used for routes)", example = "83.7230")
        @DecimalMin("-180") @DecimalMax("180") @Digits(integer = 3, fraction = 6) BigDecimal longitude
) {

    @AssertTrue(message = "latitude and longitude must be provided together")
    @Schema(hidden = true)
    public boolean isLocationComplete() {
        return (latitude == null) == (longitude == null);
    }
}
