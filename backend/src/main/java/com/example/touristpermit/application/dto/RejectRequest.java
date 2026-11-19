package com.example.touristpermit.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectRequest(
        @Schema(example = "Passport scan is unreadable") @NotBlank @Size(max = 1000) String reason
) {
}
