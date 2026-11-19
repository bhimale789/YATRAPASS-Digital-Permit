package com.example.touristpermit.staff;

import com.example.touristpermit.common.response.PageResponse;
import com.example.touristpermit.common.util.SortWhitelist;
import com.example.touristpermit.staff.dto.StaffDtos.CreateStaffRequest;
import com.example.touristpermit.staff.dto.StaffDtos.StaffCredentialsResponse;
import com.example.touristpermit.staff.dto.StaffDtos.StaffResponse;
import com.example.touristpermit.staff.dto.StaffDtos.StaffStatusRequest;
import com.example.touristpermit.staff.dto.StaffDtos.UpdateStaffRequest;
import com.example.touristpermit.user.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/admin/staff")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin: Staff", description = "Create and manage administrator and officer accounts")
public class AdminStaffController {

    private static final Set<String> SORTABLE = Set.of("email", "createdAt", "lastLoginAt", "role");

    private final StaffService staffService;

    public AdminStaffController(StaffService staffService) {
        this.staffService = staffService;
    }

    @GetMapping
    @Operation(summary = "Search staff accounts")
    public PageResponse<StaffResponse> search(@RequestParam(required = false) String q,
                                              @RequestParam(required = false) Role role,
                                              @PageableDefault(size = 20, sort = "createdAt",
                                                      direction = Sort.Direction.DESC) Pageable pageable) {
        return staffService.search(q, role, SortWhitelist.restrict(pageable, SORTABLE));
    }

    @PostMapping
    @Operation(summary = "Create a staff account",
            description = "Returns a one-time temporary password; the user must change it at first sign-in.")
    public ResponseEntity<StaffCredentialsResponse> create(@Valid @RequestBody CreateStaffRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(staffService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update name and role")
    public StaffResponse update(@PathVariable Long id, @Valid @RequestBody UpdateStaffRequest request) {
        return staffService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Enable or disable a staff account (signs the user out when disabled)")
    public StaffResponse setStatus(@PathVariable Long id, @Valid @RequestBody StaffStatusRequest request) {
        return staffService.setEnabled(id, request.enabled());
    }

    @PostMapping("/{id}/reset-password")
    @Operation(summary = "Issue a new temporary password (signs the user out)")
    public ResponseEntity<StaffCredentialsResponse> resetPassword(@PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(staffService.resetPassword(id));
    }

    @PostMapping("/{id}/reset-mfa")
    @Operation(summary = "Turn off two-factor authentication for a user who lost their device")
    public StaffResponse resetMfa(@PathVariable Long id) {
        return staffService.resetMfa(id);
    }
}
