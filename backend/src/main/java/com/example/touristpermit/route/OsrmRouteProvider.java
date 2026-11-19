package com.example.touristpermit.route;

import com.example.touristpermit.config.AppProperties;
import com.example.touristpermit.road.Coordinate;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Driving routes from an OSRM HTTP API ({@code /route/v1/driving/{lng},{lat};{lng},{lat}}).
 * Coordinates are never logged.
 */
@Component
public class OsrmRouteProvider implements RouteProvider {

    private static final Logger log = LoggerFactory.getLogger(OsrmRouteProvider.class);

    /** Points closer than this to the simplified line are dropped. */
    static final double SIMPLIFY_TOLERANCE_METERS = 15;

    private final RestClient client;

    public OsrmRouteProvider(RestClient.Builder builder, AppProperties properties) {
        AppProperties.Routing cfg = properties.routing();
        HttpClient http = HttpClient.newBuilder().connectTimeout(cfg.timeout()).followRedirects(HttpClient.Redirect.NORMAL).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(cfg.timeout());
        this.client = builder
                .baseUrl(cfg.osrmUrl().replaceAll("/+$", ""))
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.USER_AGENT, "tourist-permit-system/1.0 (route lookup)")
                .build();
    }

    @Override
    public Optional<RouteResult> route(Coordinate from, Coordinate to) {
        String path = "/route/v1/driving/%s,%s;%s,%s?overview=full&geometries=geojson".formatted(
                fmt(from.lng()), fmt(from.lat()), fmt(to.lng()), fmt(to.lat()));
        JsonNode body;
        try {
            body = client.get().uri(path).retrieve().body(JsonNode.class);
        } catch (RestClientResponseException e) {
            // OSRM answers 400 with code NoRoute/NoSegment when points cannot be routed.
            if (isNoRoute(errorCode(e))) {
                return Optional.empty();
            }
            log.warn("Routing service returned HTTP {}", e.getStatusCode().value());
            throw new RouteUnavailableException();
        } catch (RestClientException e) {
            log.warn("Routing service unreachable: {}", e.getClass().getSimpleName());
            throw new RouteUnavailableException();
        }
        if (body == null) {
            throw new RouteUnavailableException();
        }
        String code = body.path("code").asText();
        if (isNoRoute(code)) {
            return Optional.empty();
        }
        JsonNode route = body.path("routes").path(0);
        if (!"Ok".equals(code) || route.isMissingNode()) {
            log.warn("Routing service returned code {}", code);
            throw new RouteUnavailableException();
        }
        List<Coordinate> coordinates = new ArrayList<>();
        for (JsonNode p : route.path("geometry").path("coordinates")) {
            coordinates.add(new Coordinate(p.get(1).asDouble(), p.get(0).asDouble())); // GeoJSON is [lng, lat]
        }
        if (coordinates.size() < 2) {
            return Optional.empty();
        }
        // Full geometry keeps the road's true shape; simplification drops redundant points
        // (e.g. ~14k -> ~1.5k for Kathmandu-Jomsom) to keep responses small for phones.
        return Optional.of(new RouteResult(route.path("distance").asDouble(), route.path("duration").asDouble(),
                Polylines.simplify(coordinates, SIMPLIFY_TOLERANCE_METERS)));
    }

    /** OSRM error code from an error response, or "" when the body is not OSRM JSON (e.g. a proxy error page). */
    private static String errorCode(RestClientResponseException e) {
        try {
            JsonNode error = e.getResponseBodyAs(JsonNode.class);
            return error == null ? "" : error.path("code").asText();
        } catch (RuntimeException notJson) {
            return "";
        }
    }

    private static boolean isNoRoute(String code) {
        return "NoRoute".equals(code) || "NoSegment".equals(code);
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }
}
