package com.example.touristpermit.account.dto;

import com.example.touristpermit.user.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** Request/response records for the signed-in user's own account. */
public final class AccountDtos {

    private AccountDtos() {
    }

    public record AccountResponse(
            Long id,
            String email,
            Role role,
            String displayName,
            boolean mfaEnabled,
            @Schema(description = "Unused recovery codes left (0 when 2FA is off)") long recoveryCodesRemaining,
            boolean passwordChangeRequired,
            Instant lastLoginAt
    ) {
    }

    public record ChangePasswordRequest(
            @NotBlank @Size(max = 128) String currentPassword,
            @Schema(description = "8-128 chars with a letter and a digit (12+ for staff)")
            @NotBlank @Size(min = 8, max = 128)
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "must contain at least one letter and one digit")
            String newPassword
    ) {
        @Override
        public String toString() {
            return "ChangePasswordRequest[***]";
        }
    }

    public record CodeRequest(
            @Schema(description = "6-digit authenticator code (or a recovery code where accepted)", example = "123456")
            @NotBlank @Size(max = 20) String code
    ) {
        @Override
        public String toString() {
            return "CodeRequest[***]";
        }
    }

    public record RecoveryCodesResponse(
            @Schema(description = "Shown only once. Each code works one time.") List<String> recoveryCodes
    ) {
    }
}
