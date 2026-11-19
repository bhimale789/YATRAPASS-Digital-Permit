package com.example.touristpermit.dashboard;

import com.example.touristpermit.dashboard.dto.OfficerDashboardResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/officer/dashboard")
@PreAuthorize("hasRole('OFFICER')")
@Tag(name = "Officer: Dashboard")
public class OfficerDashboardController {

    private final OfficerDashboardService dashboardService;

    public OfficerDashboardController(OfficerDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    @Operation(summary = "My scan activity", description = "Today's scan counts and recent scans by the signed-in "
            + "officer, plus the number of permits valid today.")
    public OfficerDashboardResponse get() {
        return dashboardService.dashboard();
    }
}
