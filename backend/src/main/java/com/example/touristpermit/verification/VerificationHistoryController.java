package com.example.touristpermit.verification;

import com.example.touristpermit.common.response.PageResponse;
import com.example.touristpermit.common.util.SortWhitelist;
import com.example.touristpermit.verification.dto.VerificationLogResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/verifications")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin: Verification History")
public class VerificationHistoryController {

    private static final Set<String> SORTABLE = Set.of("verifiedAt", "result");

    private final VerificationService verificationService;

    public VerificationHistoryController(VerificationService verificationService) {
        this.verificationService = verificationService;
    }

    @GetMapping
    @Operation(summary = "QR verification history", description = "Filter by result, permit and time range.")
    public PageResponse<VerificationLogResponse> history(
            @RequestParam(required = false) VerificationResult result,
            @RequestParam(required = false) Long permitId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(sort = "verifiedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return verificationService.history(result, permitId, from, to, SortWhitelist.restrict(pageable, SORTABLE));
    }
}
