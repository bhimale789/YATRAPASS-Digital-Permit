package com.example.touristpermit.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Tourist self-registration. Role is always TOURIST and cannot be chosen by the client.")
public record RegisterRequest(
        @Schema(example = "john.smith@example.com")
        @NotBlank @Email @Size(max = 255) String email,

        @Schema(example = "Str0ngPassw0rd!", description = "8-128 chars, at least one letter and one digit")
        @NotBlank @Size(min = 8, max = 128)
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "must contain at least one letter and one digit")
        String password,

        @Schema(example = "John")
        @NotBlank @Size(max = 100) String firstName,

        @Schema(example = "Smith")
        @NotBlank @Size(max = 100) String lastName
) {
    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + "]";
    }
}
