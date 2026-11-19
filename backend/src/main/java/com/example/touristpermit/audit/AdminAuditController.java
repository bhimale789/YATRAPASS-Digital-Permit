package com.example.touristpermit.audit;

import com.example.touristpermit.common.response.PageResponse;
import com.example.touristpermit.common.util.SortWhitelist;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin: Audit Log")
public class AdminAuditController {

    private static final Set<String> SORTABLE = Set.of("createdAt", "action");

    private final AuditService auditService;

    public AdminAuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    @Operation(summary = "Search the audit trail")
    public PageResponse<AuditLogResponse> search(@RequestParam(required = false) AuditAction action,
                                                 @RequestParam(required = false) String entityType,
                                                 @RequestParam(required = false) String entityId,
                                                 @PageableDefault(size = 50, sort = "createdAt",
                                                         direction = Sort.Direction.DESC) Pageable pageable) {
        return auditService.search(action, entityType, entityId, SortWhitelist.restrict(pageable, SORTABLE));
    }
}
