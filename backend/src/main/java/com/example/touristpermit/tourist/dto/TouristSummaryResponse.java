package com.example.touristpermit.tourist.dto;

import com.example.touristpermit.common.util.Masking;
import com.example.touristpermit.tourist.TouristProfile;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/** Tourist list item for administrators; passport is masked. */
public record TouristSummaryResponse(
        Long id,
        String fullName,
        String email,
        String nationality,
        @Schema(example = "****5678") String passportNumberMasked,
        boolean enabled,
        boolean profileComplete,
        Instant registeredAt
) {

    public static TouristSummaryResponse from(TouristProfile p) {
        return new TouristSummaryResponse(p.getId(), p.getFullName(), p.getUser().getEmail(), p.getNationality(),
                Masking.passport(p.getPassportNumber()), p.getUser().isEnabled(), p.isComplete(), p.getCreatedAt());
    }
}
