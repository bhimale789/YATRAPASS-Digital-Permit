package com.example.touristpermit.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @Schema(example = "john.smith@example.com") @NotBlank @Email @Size(max = 255) String email,
        @Schema(example = "Str0ngPassw0rd!") @NotBlank @Size(max = 128) String password
) {
    @Override
    public String toString() {
        return "LoginRequest[email=" + email + "]";
    }
}
