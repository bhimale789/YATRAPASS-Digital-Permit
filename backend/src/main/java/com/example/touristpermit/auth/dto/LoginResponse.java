package com.example.touristpermit.auth.dto;

import com.example.touristpermit.user.Role;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Result of a login step. Either a full session (tokens) or, for accounts with two-factor
 * authentication, a short-lived {@code mfaToken} to send with the code to /auth/login/mfa.
 */
public record LoginResponse(
        @Schema(description = "Short-lived JWT; send as 'Authorization: Bearer <token>'") String accessToken,
        @Schema(description = "Opaque single-use token for /auth/refresh") String refreshToken,
        @Schema(example = "Bearer") String tokenType,
        @Schema(example = "900") Long expiresIn,
        UserInfo user,
        @Schema(description = "True when a second factor is required; no session tokens are returned yet")
        boolean mfaRequired,
        @Schema(description = "5-minute token for POST /auth/login/mfa") String mfaToken,
        @Schema(description = "True when the user must set a new password before using the system")
        boolean passwordChangeRequired
) {

    public record UserInfo(Long id, String email, Role role) {
    }

    public static LoginResponse session(String accessToken, String refreshToken, long expiresIn, UserInfo user,
                                        boolean passwordChangeRequired) {
        return new LoginResponse(accessToken, refreshToken, "Bearer", expiresIn, user, false, null,
                passwordChangeRequired);
    }

    public static LoginResponse mfaChallenge(String mfaToken, UserInfo user) {
        return new LoginResponse(null, null, null, null, user, true, mfaToken, false);
    }

    @Override
    public String toString() {
        return "LoginResponse[user=" + user + ", mfaRequired=" + mfaRequired + "]";
    }
}
