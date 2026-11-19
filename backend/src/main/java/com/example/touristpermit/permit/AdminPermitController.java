package com.example.touristpermit.permit;

import com.example.touristpermit.common.response.PageResponse;
import com.example.touristpermit.common.util.SortWhitelist;
import com.example.touristpermit.permit.dto.CancelPermitRequest;
import com.example.touristpermit.permit.dto.PermitResponse;
import com.example.touristpermit.qr.QrActionRequest;
import com.example.touristpermit.qr.QrImageResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/admin/permits")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin: Permits", description = "Search and manage issued permits")
public class AdminPermitController {

    private static final Set<String> SORTABLE = Set.of("validFrom", "validUntil", "issueDate", "status",
            "permitNumber", "createdAt");

    private final PermitService permitService;

    public AdminPermitController(PermitService permitService) {
        this.permitService = permitService;
    }

    @GetMapping
    @Operation(summary = "Search permits", description = "q matches permit number, application number, tourist "
            + "name, passport number, area name or code. Filter by status for active/expired/cancelled lists.")
    public PageResponse<PermitResponse> search(@RequestParam(required = false) String q,
                                               @RequestParam(required = false) PermitStatus status,
                                               @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC)
                                               Pageable pageable) {
        return permitService.search(q, status, SortWhitelist.restrict(pageable, SORTABLE));
    }

    @GetMapping("/expiring")
    @Operation(summary = "Active permits expiring within N days")
    public PageResponse<PermitResponse> expiring(@RequestParam(defaultValue = "7") @Min(0) @Max(90) int days,
                                                 @PageableDefault(size = 10) Pageable pageable) {
        return permitService.expiringWithin(days, Pageable.ofSize(pageable.getPageSize())
                .withPage(pageable.getPageNumber()));
    }

    @GetMapping("/{id}")
    public PermitResponse get(@PathVariable Long id) {
        return permitService.getForAdmin(id);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel an active permit (ACTIVE → CANCELLED); its QR stops verifying")
    public PermitResponse cancel(@PathVariable Long id, @Valid @RequestBody CancelPermitRequest request) {
        return permitService.cancel(id, request.reason());
    }

    @GetMapping(value = "/{id}/qr", produces = MediaType.IMAGE_PNG_VALUE)
    @Operation(summary = "View a permit's active QR code")
    public ResponseEntity<byte[]> qr(@PathVariable Long id) {
        PermitResponse permit = permitService.getForAdmin(id);
        return QrImageResponses.png(permitService.qrPngForAdmin(id), permit.permitNumber(), false);
    }

    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Printable PDF of a permit", description = "Same document the tourist downloads.")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean download) {
        return permitService.permitPdfForAdmin(id).toResponse(download);
    }

    @PostMapping("/{id}/qr/regenerate")
    @Operation(summary = "Revoke the current QR and issue a new one",
            description = "The old QR immediately verifies as TOKEN_REVOKED. ACTIVE permits only.")
    public PermitResponse regenerateQr(@PathVariable Long id, @Valid @RequestBody QrActionRequest request) {
        return permitService.regenerateQr(id, request.reason());
    }

    @PostMapping("/{id}/qr/revoke")
    @Operation(summary = "Revoke the current QR without a replacement")
    public PermitResponse revokeQr(@PathVariable Long id, @Valid @RequestBody QrActionRequest request) {
        return permitService.revokeQr(id, request.reason());
    }

    @PostMapping("/{id}/complete")
    @Operation(summary = "Mark an active permit as completed (ACTIVE → COMPLETED)")
    public PermitResponse complete(@PathVariable Long id) {
        return permitService.complete(id);
    }
}
