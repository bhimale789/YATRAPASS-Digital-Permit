package com.example.touristpermit.staff.dto;

import com.example.touristpermit.user.Role;
import com.example.touristpermit.user.User;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Staff (administrator / officer) account management records. */
public final class StaffDtos {

    private StaffDtos() {
    }

    public record StaffResponse(
            Long id,
            String email,
            String displayName,
            Role role,
            boolean enabled,
            boolean mfaEnabled,
            boolean passwordChangeRequired,
            Instant lastLoginAt,
            Instant createdAt
    ) {
        public static StaffResponse from(User u) {
            return new StaffResponse(u.getId(), u.getEmail(), u.getDisplayName(), u.getRole(), u.isEnabled(),
                    u.isTotpEnabled(), u.isMustChangePassword(), u.getLastLoginAt(), u.getCreatedAt());
        }
    }

    public record CreateStaffRequest(
            @Schema(example = "officer.jomsom@example.gov.np") @NotBlank @Email @Size(max = 255) String email,
            @Schema(example = "Jomsom Checkpoint") @NotBlank @Size(max = 100) String displayName,
            @Schema(description = "ADMIN or OFFICER") @NotNull Role role
    ) {
    }

    public record UpdateStaffRequest(
            @NotBlank @Size(max = 100) String displayName,
            @Schema(description = "ADMIN or OFFICER") @NotNull Role role
    ) {
    }

    public record StaffStatusRequest(@NotNull Boolean enabled) {
    }

    /** Returned once on creation / password reset; the temporary password is never stored in plaintext. */
    public record StaffCredentialsResponse(
            StaffResponse staff,
            @Schema(description = "Give this to the staff member securely; they must change it at first login")
            String temporaryPassword
    ) {
        @Override
        public String toString() {
            return "StaffCredentialsResponse[staff=" + staff.id() + "]";
        }
    }
}
