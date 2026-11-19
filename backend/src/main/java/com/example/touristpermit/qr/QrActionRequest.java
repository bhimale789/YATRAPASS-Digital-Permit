package com.example.touristpermit.qr;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record QrActionRequest(
        @Schema(example = "Tourist reported phone stolen") @NotBlank @Size(max = 500) String reason
) {
}
