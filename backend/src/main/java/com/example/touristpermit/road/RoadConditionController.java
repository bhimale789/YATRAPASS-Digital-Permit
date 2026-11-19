package com.example.touristpermit.road;

import com.example.touristpermit.road.dto.RoadConditionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/road-conditions")
@PreAuthorize("isAuthenticated()")
@Tag(name = "Road Conditions", description = "Current road conditions for maps (all signed-in users)")
public class RoadConditionController {

    private final RoadConditionService service;

    public RoadConditionController(RoadConditionService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Active road conditions",
            description = "Optionally filtered to an area; general conditions (no area) are included unless "
                    + "includeGeneral=false. At most 500, newest first.")
    public List<RoadConditionResponse> active(@RequestParam(required = false) Long areaId,
                                              @RequestParam(defaultValue = "true") boolean includeGeneral) {
        return service.activeForMap(areaId, includeGeneral);
    }
}
