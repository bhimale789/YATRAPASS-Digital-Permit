package com.example.touristpermit.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Used by both /refresh and /logout. */
public record RefreshRequest(@NotBlank @Size(max = 200) String refreshToken) {

    @Override
    public String toString() {
        return "RefreshRequest[***]";
    }
}
