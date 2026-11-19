package com.example.touristpermit.permit;

import com.example.touristpermit.common.response.PageResponse;
import com.example.touristpermit.common.util.SortWhitelist;
import com.example.touristpermit.permit.dto.PermitResponse;
import com.example.touristpermit.qr.QrImageResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/permits")
@PreAuthorize("hasRole('TOURIST')")
@Tag(name = "Tourist: Permits", description = "View issued permits")
public class PermitController {

    private static final Set<String> SORTABLE = Set.of("validFrom", "validUntil", "issueDate", "status");

    private final PermitService permitService;

    public PermitController(PermitService permitService) {
        this.permitService = permitService;
    }

    @GetMapping
    @Operation(summary = "List my permits", description = "Filter by status, e.g. ACTIVE or EXPIRED.")
    public PageResponse<PermitResponse> list(@RequestParam(required = false) PermitStatus status,
                                             @PageableDefault(sort = "validFrom", direction = Sort.Direction.DESC)
                                             Pageable pageable) {
        return permitService.listMine(status, SortWhitelist.restrict(pageable, SORTABLE));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one of my permits")
    public PermitResponse get(@PathVariable Long id) {
        return permitService.getMine(id);
    }

    @GetMapping(value = "/{id}/qr", produces = MediaType.IMAGE_PNG_VALUE)
    @Operation(summary = "QR code image for my ACTIVE permit",
            description = "PNG encoding only <APP_BASE_URL>/verify/<token>. Use download=true to save as a file.")
    public ResponseEntity<byte[]> qr(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean download) {
        PermitResponse permit = permitService.getMine(id);
        return QrImageResponses.png(permitService.myQrPng(id), permit.permitNumber(), download);
    }

    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Printable PDF of my permit",
            description = "Available in any status. The QR code is included only while the permit is usable for "
                    + "travel; otherwise the PDF is watermarked with its status. Use download=true to save as a file.")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean download) {
        return permitService.myPermitPdf(id).toResponse(download);
    }
}
