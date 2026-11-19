package com.example.touristpermit.road.dto;

import com.example.touristpermit.road.Coordinate;
import com.example.touristpermit.road.GeometryType;
import com.example.touristpermit.road.RoadStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record RoadConditionRequest(
        @Schema(example = "Landslide near Tatopani") @NotBlank @Size(max = 150) String title,
        @Schema(example = "Single-lane traffic, expect 1-2 hour delays") @Size(max = 1000) String description,
        @NotNull RoadStatus status,
        @NotNull GeometryType geometryType,
        @Schema(description = "1 coordinate for POINT, 2-500 for LINE")
        @NotNull @Size(min = 1, max = 500) List<@Valid @NotNull Coordinate> coordinates,
        @Schema(description = "Optional affected destination") Long areaId,
        @Schema(description = "Shown on tourist/officer maps when true") @NotNull Boolean active
) {
}
