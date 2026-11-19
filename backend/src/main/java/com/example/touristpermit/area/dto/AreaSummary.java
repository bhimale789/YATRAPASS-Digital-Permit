package com.example.touristpermit.area.dto;

import com.example.touristpermit.area.PermitArea;

public record AreaSummary(Long id, String name, String code, String photoUrl) {

    public static AreaSummary from(PermitArea a) {
        return new AreaSummary(a.getId(), a.getName(), a.getCode(), AreaPhotos.url(a));
    }
}
