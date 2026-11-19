package com.example.touristpermit.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MfaLoginRequest(
        @Schema(description = "mfaToken from the password step") @NotBlank @Size(max = 2000) String mfaToken,
        @Schema(description = "6-digit authenticator code or a recovery code (XXXXX-XXXXX)", example = "123456")
        @NotBlank @Size(max = 20) String code
) {
    @Override
    public String toString() {
        return "MfaLoginRequest[***]";
    }
}
