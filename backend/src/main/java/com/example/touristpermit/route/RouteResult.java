package com.example.touristpermit.route;

import com.example.touristpermit.road.Coordinate;

import java.util.List;

/** A computed driving route. */
public record RouteResult(double distanceMeters, double durationSeconds, List<Coordinate> coordinates) {
}
