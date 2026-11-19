package com.example.touristpermit.road.dto;

import com.example.touristpermit.area.dto.AreaSummary;
import com.example.touristpermit.road.Coordinate;
import com.example.touristpermit.road.GeometryType;
import com.example.touristpermit.road.RoadCondition;
import com.example.touristpermit.road.RoadStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

public record RoadConditionResponse(
        Long id,
        String title,
        String description,
        RoadStatus status,
        GeometryType geometryType,
        List<Coordinate> coordinates,
        AreaSummary area,
        boolean active,
        Instant updatedAt,
        @Schema(description = "Administrator views only") String updatedBy
) {

    /** Public view (tourists, officers): no staff identities. */
    public static RoadConditionResponse publicView(RoadCondition r) {
        return of(r, null);
    }

    public static RoadConditionResponse adminView(RoadCondition r) {
        return of(r, r.getUpdatedBy() == null ? null : r.getUpdatedBy().getEmail());
    }

    private static RoadConditionResponse of(RoadCondition r, String updatedBy) {
        return new RoadConditionResponse(r.getId(), r.getTitle(), r.getDescription(), r.getStatus(),
                r.getGeometryType(), List.copyOf(r.getCoordinates()),
                r.getArea() == null ? null : AreaSummary.from(r.getArea()), r.isActive(), r.getUpdatedAt(), updatedBy);
    }
}
