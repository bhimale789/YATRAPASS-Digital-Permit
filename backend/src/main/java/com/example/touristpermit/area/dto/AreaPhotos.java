package com.example.touristpermit.area.dto;

import com.example.touristpermit.area.PermitArea;

/** Builds the public, cache-busting photo URL for an area. */
final class AreaPhotos {

    private AreaPhotos() {
    }

    static String url(PermitArea area) {
        if (!area.hasPhoto()) {
            return null;
        }
        return "/api/v1/areas/" + area.getId() + "/photo?v=" + area.getPhotoUpdatedAt().toEpochMilli();
    }
}
