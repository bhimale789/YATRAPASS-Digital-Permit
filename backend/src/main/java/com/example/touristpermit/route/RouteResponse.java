package com.example.touristpermit.route;

import com.example.touristpermit.road.Coordinate;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Driving route from a start point to a destination")
public record RouteResponse(
        Place from,
        Place to,
        @Schema(example = "412300") double distanceMeters,
        @Schema(example = "40500") double durationSeconds,
        @Schema(description = "Route polyline (lat/lng)") List<Coordinate> coordinates,
        @Schema(example = "Routing © OSRM · Map data © OpenStreetMap contributors") String attribution
) {

    public record Place(String label, double lat, double lng) {
    }
}
