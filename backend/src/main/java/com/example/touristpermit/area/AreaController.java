package com.example.touristpermit.area;

import com.example.touristpermit.area.dto.AreaResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import com.example.touristpermit.common.exception.ApiException;
import com.example.touristpermit.route.RouteResponse;
import com.example.touristpermit.route.RouteService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/v1/areas")
@Tag(name = "Areas", description = "Public list of permit-required areas")
@SecurityRequirements
public class AreaController {

    private final AreaService areaService;
    private final AreaPhotoService photoService;
    private final RouteService routeService;

    public AreaController(AreaService areaService, AreaPhotoService photoService, RouteService routeService) {
        this.areaService = areaService;
        this.photoService = photoService;
        this.routeService = routeService;
    }

    @GetMapping
    @Operation(summary = "List active areas")
    public List<AreaResponse> list() {
        return areaService.listActive();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an active area")
    public AreaResponse get(@PathVariable Long id) {
        return areaService.getActive(id);
    }

    @GetMapping("/{id}/route")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Driving route to the destination",
            description = "From the given point, or from the configured default start (Pokhara) when omitted. "
                    + "422 if the destination has no location or no road route exists; 503 if the routing service is down.")
    public RouteResponse route(@PathVariable Long id,
                               @RequestParam(required = false) @DecimalMin("-90") @DecimalMax("90") Double fromLat,
                               @RequestParam(required = false) @DecimalMin("-180") @DecimalMax("180") Double fromLng) {
        if ((fromLat == null) != (fromLng == null)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "fromLat and fromLng must be given together");
        }
        return routeService.routeTo(id, fromLat, fromLng);
    }

    @GetMapping("/{id}/photo")
    @Operation(summary = "Destination photo", description = "Use the versioned photoUrl from the area response; "
            + "it changes whenever the photo is replaced, so responses can be cached.")
    public ResponseEntity<Resource> photo(@PathVariable Long id) {
        AreaPhotoService.Photo photo = photoService.load(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.mimeType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(photo.resource());
    }
}
