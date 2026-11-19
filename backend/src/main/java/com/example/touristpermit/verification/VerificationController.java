package com.example.touristpermit.verification;

import com.example.touristpermit.common.util.RequestInfo;
import com.example.touristpermit.verification.dto.PermitVerificationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/permits/verify")
@PreAuthorize("hasAnyRole('ADMIN', 'OFFICER')")
@Tag(name = "QR Verification", description = "Verify scanned permit QR codes (ADMIN / OFFICER)")
public class VerificationController {

    private final VerificationService verificationService;

    public VerificationController(VerificationService verificationService) {
        this.verificationService = verificationService;
    }

    @GetMapping("/{token}")
    @Operation(summary = "Verify a QR token",
            description = """
                    Checks token existence, revocation, permit status, validity dates and (optionally) the \
                    checkpoint area. Always returns 200 with `valid` true/false; every call is logged. \
                    Possible reasons: INVALID_TOKEN, TOKEN_REVOKED, PERMIT_NOT_FOUND, PERMIT_CANCELLED, \
                    PERMIT_EXPIRED, PERMIT_NOT_ACTIVE, PERMIT_NOT_YET_VALID, AREA_MISMATCH.""")
    public ResponseEntity<PermitVerificationResponse> verify(
            @PathVariable @Size(max = 200) String token,
            @Parameter(description = "Checkpoint area id; when given, the permit must be for this area")
            @RequestParam(required = false) Long areaId,
            @Parameter(description = "Only stored when VERIFICATION_LOCATION_ENABLED=true")
            @RequestParam(required = false) @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
            @RequestParam(required = false) @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
            HttpServletRequest request) {
        PermitVerificationResponse response = verificationService.verify(token,
                new VerificationService.ScanContext(areaId, latitude, longitude), RequestInfo.from(request));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }
}
