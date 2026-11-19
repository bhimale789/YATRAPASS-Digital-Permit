package com.example.touristpermit.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record ApplicationRequest(
        @Schema(example = "1") @NotNull Long areaId,
        @Schema(example = "Trekking with a registered guide") @NotBlank @Size(min = 5, max = 1000) String purpose,
        @Schema(example = "2026-10-10") @NotNull LocalDate startDate,
        @Schema(example = "2026-10-15") @NotNull LocalDate endDate
) {
}
