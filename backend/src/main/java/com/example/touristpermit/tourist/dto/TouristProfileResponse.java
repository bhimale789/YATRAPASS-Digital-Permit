package com.example.touristpermit.tourist.dto;

import com.example.touristpermit.tourist.Gender;
import com.example.touristpermit.tourist.TouristProfile;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/** The tourist's own profile (returned only to its owner). */
public record TouristProfileResponse(
        Long id,
        String email,
        String firstName,
        String lastName,
        @Schema(description = "Shown only to the profile owner") String passportNumber,
        String nationality,
        LocalDate dateOfBirth,
        Gender gender,
        String phone,
        String address,
        String emergencyContact,
        @Schema(description = "True when all fields required to apply for a permit are filled in") boolean complete
) {

    public static TouristProfileResponse from(TouristProfile p) {
        return new TouristProfileResponse(p.getId(), p.getUser().getEmail(), p.getFirstName(), p.getLastName(),
                p.getPassportNumber(), p.getNationality(), p.getDateOfBirth(), p.getGender(), p.getPhone(),
                p.getAddress(), p.getEmergencyContact(), p.isComplete());
    }
}
