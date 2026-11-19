package com.example.touristpermit.tourist;

import com.example.touristpermit.tourist.dto.TouristProfileResponse;
import com.example.touristpermit.tourist.dto.UpdateProfileRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tourists/me")
@PreAuthorize("hasRole('TOURIST')")
@Tag(name = "Tourist: Profile")
public class TouristController {

    private final TouristService touristService;

    public TouristController(TouristService touristService) {
        this.touristService = touristService;
    }

    @GetMapping
    @Operation(summary = "Get my profile")
    public TouristProfileResponse get() {
        return touristService.getMyProfile();
    }

    @PutMapping
    @Operation(summary = "Update my profile", description = "Passport, nationality, date of birth and phone are "
            + "required before a permit application can be submitted.")
    public TouristProfileResponse update(@Valid @RequestBody UpdateProfileRequest request) {
        return touristService.updateMyProfile(request);
    }
}
