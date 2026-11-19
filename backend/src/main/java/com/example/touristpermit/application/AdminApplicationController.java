package com.example.touristpermit.application;

import com.example.touristpermit.application.dto.ApplicationResponse;
import com.example.touristpermit.application.dto.RejectRequest;
import com.example.touristpermit.common.response.PageResponse;
import com.example.touristpermit.common.util.SortWhitelist;
import com.example.touristpermit.permit.PermitService;
import com.example.touristpermit.permit.dto.PermitResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/admin/applications")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin: Applications", description = "Review, approve and reject permit applications")
public class AdminApplicationController {

    private static final Set<String> SORTABLE = Set.of("submittedAt", "requestedStartDate", "status", "applicationNumber");

    private final ApplicationService applicationService;
    private final PermitService permitService;

    public AdminApplicationController(ApplicationService applicationService, PermitService permitService) {
        this.applicationService = applicationService;
        this.permitService = permitService;
    }

    @GetMapping
    @Operation(summary = "Search applications",
            description = "q matches application number, tourist name, passport number, area name or code.")
    public PageResponse<ApplicationResponse> search(@RequestParam(required = false) String q,
                                                    @RequestParam(required = false) ApplicationStatus status,
                                                    @PageableDefault(sort = "submittedAt",
                                                            direction = Sort.Direction.DESC) Pageable pageable) {
        return applicationService.search(q, status, SortWhitelist.restrict(pageable, SORTABLE));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Application detail with tourist and documents")
    public ApplicationResponse get(@PathVariable Long id) {
        return applicationService.getForAdmin(id);
    }

    @PostMapping("/{id}/review")
    @Operation(summary = "Start review (PENDING → UNDER_REVIEW)")
    public ApplicationResponse startReview(@PathVariable Long id) {
        return applicationService.startReview(id);
    }

    @PostMapping("/{id}/approve")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Approve application and issue the permit",
            description = "PENDING/UNDER_REVIEW → APPROVED. Requires an uploaded PASSPORT document. "
                    + "Creates an ACTIVE permit (PRM-YYYY-NNNNNN) with a secure QR verification token.")
    public PermitResponse approve(@PathVariable Long id) {
        return permitService.approve(id);
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject application (PENDING/UNDER_REVIEW → REJECTED)")
    public ApplicationResponse reject(@PathVariable Long id, @Valid @RequestBody RejectRequest request) {
        return applicationService.reject(id, request.reason());
    }
}
