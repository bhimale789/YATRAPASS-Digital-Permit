package com.example.touristpermit.road;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/** WGS84 coordinate. */
public record Coordinate(
        @Schema(example = "28.5967") @NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
        @Schema(example = "83.8203") @NotNull @DecimalMin("-180") @DecimalMax("180") Double lng
) {
}
