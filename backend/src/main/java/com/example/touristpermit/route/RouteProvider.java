package com.example.touristpermit.route;

import com.example.touristpermit.road.Coordinate;

import java.util.Optional;

/** Computes driving routes. Implementations must not log or persist the coordinates they receive. */
public interface RouteProvider {

    /**
     * @return the route, or empty when no drivable route exists between the points
     * @throws RouteUnavailableException when the routing service cannot be reached or fails
     */
    Optional<RouteResult> route(Coordinate from, Coordinate to);
}
