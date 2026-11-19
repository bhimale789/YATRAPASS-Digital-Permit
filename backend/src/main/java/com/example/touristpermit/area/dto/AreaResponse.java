package com.example.touristpermit.area.dto;

import com.example.touristpermit.area.PermitArea;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

public record AreaResponse(
        Long id,
        String name,
        String code,
        String description,
        String district,
        String province,
        boolean permitRequired,
        int maximumDays,
        BigDecimal fee,
        boolean active,
        @Schema(description = "Destination photo URL (absent when no photo was uploaded)",
                example = "/api/v1/areas/1/photo?v=1760000000000")
        String photoUrl,
        @Schema(description = "Destination pin (absent when not set)") BigDecimal latitude,
        BigDecimal longitude
) {

    public static AreaResponse from(PermitArea a) {
        return new AreaResponse(a.getId(), a.getName(), a.getCode(), a.getDescription(), a.getDistrict(),
                a.getProvince(), a.isPermitRequired(), a.getMaximumDays(), a.getFee(), a.isActive(),
                AreaPhotos.url(a), a.getLatitude(), a.getLongitude());
    }
}
