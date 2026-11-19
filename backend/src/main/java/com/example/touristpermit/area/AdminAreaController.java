package com.example.touristpermit.area;

import com.example.touristpermit.area.dto.AreaRequest;
import com.example.touristpermit.area.dto.AreaResponse;
import com.example.touristpermit.area.dto.AreaStatusRequest;
import com.example.touristpermit.common.response.PageResponse;
import com.example.touristpermit.common.util.SortWhitelist;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
@RequestMapping("/api/v1/admin/areas")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin: Areas", description = "Configure permit-required areas")
public class AdminAreaController {

    private static final Set<String> SORTABLE = Set.of("name", "code", "maximumDays", "active", "createdAt");

    private final AreaService areaService;
    private final AreaPhotoService photoService;

    public AdminAreaController(AreaService areaService, AreaPhotoService photoService) {
        this.areaService = areaService;
        this.photoService = photoService;
    }

    @GetMapping
    @Operation(summary = "Search all areas (including inactive)")
    public PageResponse<AreaResponse> search(@RequestParam(required = false) String q,
                                             @RequestParam(required = false) Boolean active,
                                             @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC)
                                             Pageable pageable) {
        return areaService.search(q, active, SortWhitelist.restrict(pageable, SORTABLE));
    }

    @GetMapping("/{id}")
    public AreaResponse get(@PathVariable Long id) {
        return areaService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create area")
    public AreaResponse create(@Valid @RequestBody AreaRequest request) {
        return areaService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update area")
    public AreaResponse update(@PathVariable Long id, @Valid @RequestBody AreaRequest request) {
        return areaService.update(id, request);
    }

    @PutMapping(value = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload or replace the destination photo",
            description = "JPEG, PNG or WebP up to the upload size limit. File content is verified against its type.")
    public AreaResponse uploadPhoto(@PathVariable Long id, @RequestPart("file") MultipartFile file) {
        return photoService.upload(id, file);
    }

    @DeleteMapping("/{id}/photo")
    @Operation(summary = "Remove the destination photo (cards fall back to a generated illustration)")
    public AreaResponse removePhoto(@PathVariable Long id) {
        return photoService.remove(id);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Activate or deactivate area")
    public AreaResponse setStatus(@PathVariable Long id, @Valid @RequestBody AreaStatusRequest request) {
        return areaService.setActive(id, request.active());
    }
}
