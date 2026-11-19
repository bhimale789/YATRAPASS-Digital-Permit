package com.example.touristpermit.tourist.dto;

import com.example.touristpermit.tourist.Gender;
import com.example.touristpermit.tourist.TouristProfile;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;

/** Full tourist record for an administrator's review screen. */
public record AdminTouristDetailResponse(
        Long id,
        Long userId,
        String email,
        boolean enabled,
        String firstName,
        String lastName,
        @Schema(description = "Full passport number; administrators only") String passportNumber,
        String nationality,
        LocalDate dateOfBirth,
        Gender gender,
        String phone,
        String address,
        String emergencyContact,
        boolean profileComplete,
        long applicationCount,
        long permitCount,
        Instant registeredAt,
        Instant updatedAt
) {

    public static AdminTouristDetailResponse from(TouristProfile p, long applicationCount, long permitCount) {
        return new AdminTouristDetailResponse(p.getId(), p.getUser().getId(), p.getUser().getEmail(),
                p.getUser().isEnabled(), p.getFirstName(), p.getLastName(), p.getPassportNumber(), p.getNationality(),
                p.getDateOfBirth(), p.getGender(), p.getPhone(), p.getAddress(), p.getEmergencyContact(),
                p.isComplete(), applicationCount, permitCount, p.getCreatedAt(), p.getUpdatedAt());
    }
}
