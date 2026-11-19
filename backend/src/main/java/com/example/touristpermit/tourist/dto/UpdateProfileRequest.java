package com.example.touristpermit.tourist.dto;

import com.example.touristpermit.tourist.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateProfileRequest(
        @Schema(example = "John") @NotBlank @Size(max = 100) String firstName,
        @Schema(example = "Smith") @NotBlank @Size(max = 100) String lastName,
        @Schema(example = "X12345678")
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9]{5,20}$", message = "must be 5-20 letters or digits")
        String passportNumber,
        @Schema(example = "USA") @NotBlank @Size(max = 100) String nationality,
        @Schema(example = "1990-05-14") @NotNull @Past LocalDate dateOfBirth,
        Gender gender,
        @Schema(example = "+1 555 010 0000")
        @NotBlank @Pattern(regexp = "^\\+?[0-9 ()-]{6,30}$", message = "must be a valid phone number")
        String phone,
        @Size(max = 500) String address,
        @Size(max = 255) String emergencyContact
) {
    @Override
    public String toString() {
        return "UpdateProfileRequest[***]";
    }
}
