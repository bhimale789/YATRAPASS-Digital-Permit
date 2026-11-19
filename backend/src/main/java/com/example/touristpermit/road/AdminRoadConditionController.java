package com.example.touristpermit.road;

import com.example.touristpermit.common.response.PageResponse;
import com.example.touristpermit.common.util.SortWhitelist;
import com.example.touristpermit.road.dto.RoadConditionRequest;
import com.example.touristpermit.road.dto.RoadConditionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/admin/road-conditions")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin: Road Conditions", description = "Create and update road conditions shown on maps")
public class AdminRoadConditionController {

    private static final Set<String> SORTABLE = Set.of("updatedAt", "status", "title", "createdAt");

    private final RoadConditionService service;

    public AdminRoadConditionController(RoadConditionService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Search road conditions (including inactive)")
    public PageResponse<RoadConditionResponse> search(@RequestParam(required = false) String q,
                                                      @RequestParam(required = false) RoadStatus status,
                                                      @RequestParam(required = false) Long areaId,
                                                      @RequestParam(required = false) Boolean active,
                                                      @PageableDefault(size = 50, sort = "updatedAt",
                                                              direction = Sort.Direction.DESC) Pageable pageable) {
        return service.search(q, status, areaId, active, SortWhitelist.restrict(pageable, SORTABLE));
    }

    @GetMapping("/{id}")
    public RoadConditionResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a road condition (point or road segment)")
    public RoadConditionResponse create(@Valid @RequestBody RoadConditionRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a road condition (status, geometry, note, area, visibility)")
    public RoadConditionResponse update(@PathVariable Long id, @Valid @RequestBody RoadConditionRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a road condition (prefer setting active=false to keep history)")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
