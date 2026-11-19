package com.example.touristpermit.tourist;

import com.example.touristpermit.common.response.PageResponse;
import com.example.touristpermit.tourist.dto.AccountStatusRequest;
import com.example.touristpermit.tourist.dto.AdminTouristDetailResponse;
import com.example.touristpermit.tourist.dto.TouristSummaryResponse;
import com.example.touristpermit.common.util.SortWhitelist;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/admin/tourists")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin: Tourists")
public class AdminTouristController {

    private static final Set<String> SORTABLE = Set.of("createdAt", "firstName", "lastName", "nationality");

    private final TouristService touristService;

    public AdminTouristController(TouristService touristService) {
        this.touristService = touristService;
    }

    @GetMapping
    @Operation(summary = "Search tourists", description = "Matches name, passport number or email.")
    public PageResponse<TouristSummaryResponse> search(@RequestParam(required = false) String q,
                                                       @PageableDefault(sort = "createdAt",
                                                               direction = Sort.Direction.DESC) Pageable pageable) {
        return touristService.search(q, SortWhitelist.restrict(pageable, SORTABLE));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Tourist details (includes full passport number)")
    public AdminTouristDetailResponse get(@PathVariable Long id) {
        return touristService.getForAdmin(id);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Enable or disable a tourist account")
    public AdminTouristDetailResponse setStatus(@PathVariable Long id, @Valid @RequestBody AccountStatusRequest request) {
        return touristService.setAccountEnabled(id, request.enabled());
    }
}
