package com.example.touristpermit.route;

import com.example.touristpermit.area.PermitArea;
import com.example.touristpermit.area.PermitAreaRepository;
import com.example.touristpermit.common.exception.BusinessRuleException;
import com.example.touristpermit.common.exception.ResourceNotFoundException;
import com.example.touristpermit.config.AppProperties;
import com.example.touristpermit.road.Coordinate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Driving routes to destinations. Results are cached (bounded LRU, TTL) per rounded
 * start/end pair, which keeps load on the external routing service low.
 */
@Service
public class RouteService {

    static final String ATTRIBUTION = "Routing © OSRM · Map data © OpenStreetMap contributors";
    private static final int CACHE_SIZE = 500;

    private record CacheEntry(Optional<RouteResult> result, Instant expires) {
    }

    private final PermitAreaRepository areas;
    private final RouteProvider provider;
    private final Clock clock;
    private final AppProperties.Routing config;
    private final Duration ttl;
    private final Map<String, CacheEntry> cache = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    public RouteService(PermitAreaRepository areas, RouteProvider provider, Clock clock, AppProperties properties) {
        this.areas = areas;
        this.provider = provider;
        this.clock = clock;
        this.config = properties.routing();
        this.ttl = config.cacheTtl();
    }

    /**
     * Route to an active area, from the given point or (when null) the configured default origin.
     */
    @Transactional(readOnly = true)
    public RouteResponse routeTo(Long areaId, Double fromLat, Double fromLng) {
        PermitArea area = areas.findByIdAndActiveTrue(areaId)
                .orElseThrow(() -> new ResourceNotFoundException("Area not found"));
        if (!area.hasLocation()) {
            throw new BusinessRuleException("A route is not available because this destination's location has not been set yet");
        }
        boolean custom = fromLat != null && fromLng != null;
        RouteResponse.Place from = custom
                ? new RouteResponse.Place("Your location", fromLat, fromLng)
                : new RouteResponse.Place(config.originLabel(), config.originLatitude(), config.originLongitude());
        RouteResponse.Place to = new RouteResponse.Place(area.getName(),
                area.getLatitude().doubleValue(), area.getLongitude().doubleValue());

        RouteResult result = lookup(new Coordinate(from.lat(), from.lng()), new Coordinate(to.lat(), to.lng()))
                .orElseThrow(() -> new BusinessRuleException("No driving route was found to this destination"));
        return new RouteResponse(from, to, result.distanceMeters(), result.durationSeconds(), result.coordinates(),
                ATTRIBUTION);
    }

    private Optional<RouteResult> lookup(Coordinate from, Coordinate to) {
        String key = key(from) + ";" + key(to);
        Instant now = clock.instant();
        synchronized (cache) {
            CacheEntry hit = cache.get(key);
            if (hit != null && hit.expires().isAfter(now)) {
                return hit.result();
            }
        }
        Optional<RouteResult> result = provider.route(from, to); // throws RouteUnavailableException (not cached)
        synchronized (cache) {
            cache.put(key, new CacheEntry(result, now.plus(ttl)));
        }
        return result;
    }

    /** ~11 m precision: nearby requests share a cache entry. */
    private static String key(Coordinate c) {
        return String.format(Locale.ROOT, "%.4f,%.4f", c.lat(), c.lng());
    }
}
