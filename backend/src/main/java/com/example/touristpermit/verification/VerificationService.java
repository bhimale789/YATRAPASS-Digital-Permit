package com.example.touristpermit.verification;

import com.example.touristpermit.audit.AuditAction;
import com.example.touristpermit.audit.AuditService;
import com.example.touristpermit.common.response.PageResponse;
import com.example.touristpermit.common.util.RequestInfo;
import com.example.touristpermit.common.util.SecureTokens;
import com.example.touristpermit.config.AppProperties;
import com.example.touristpermit.permit.Permit;
import com.example.touristpermit.permit.PermitStatus;
import com.example.touristpermit.qr.QrTokenService;
import com.example.touristpermit.qr.TokenStatus;
import com.example.touristpermit.qr.VerificationToken;
import com.example.touristpermit.qr.VerificationTokenRepository;
import com.example.touristpermit.security.CurrentUser;
import com.example.touristpermit.user.UserRepository;
import com.example.touristpermit.verification.dto.PermitVerificationResponse;
import com.example.touristpermit.verification.dto.VerificationLogResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Verifies scanned QR tokens and logs every attempt (business rules 7-11).
 * Order of checks: token format → token exists → token revoked → permit status →
 * validity dates → (optional) checkpoint area.
 */
@Service
public class VerificationService {

    private static final Logger log = LoggerFactory.getLogger(VerificationService.class);
    private static final Pattern TOKEN = Pattern.compile(QrTokenService.TOKEN_PATTERN);

    /** Optional checkpoint context supplied by the scanning device. */
    public record ScanContext(Long checkpointAreaId, BigDecimal latitude, BigDecimal longitude) {
    }

    private record Outcome(Permit permit, VerificationReason reason) {
        boolean valid() {
            return reason == null;
        }
    }

    private final VerificationTokenRepository tokens;
    private final PermitVerificationRepository verifications;
    private final UserRepository users;
    private final AuditService audit;
    private final Clock clock;
    private final boolean locationEnabled;

    public VerificationService(VerificationTokenRepository tokens, PermitVerificationRepository verifications,
                               UserRepository users, AuditService audit, Clock clock,
                               AppProperties properties) {
        this.tokens = tokens;
        this.verifications = verifications;
        this.users = users;
        this.audit = audit;
        this.clock = clock;
        this.locationEnabled = properties.verification().locationEnabled();
    }

    @Transactional
    public PermitVerificationResponse verify(String rawToken, ScanContext context, RequestInfo requestInfo) {
        Instant now = clock.instant();
        Outcome outcome = evaluate(rawToken, context, LocalDate.now(clock));
        record(outcome, context, requestInfo, now);
        return toResponse(outcome, now);
    }

    private Outcome evaluate(String rawToken, ScanContext context, LocalDate today) {
        if (rawToken == null || !TOKEN.matcher(rawToken).matches()) {
            return new Outcome(null, VerificationReason.INVALID_TOKEN);
        }
        VerificationToken token = tokens.findByTokenHash(SecureTokens.sha256Hex(rawToken)).orElse(null);
        if (token == null) {
            return new Outcome(null, VerificationReason.INVALID_TOKEN);
        }
        Permit permit = token.getPermit();
        if (permit == null) {
            return new Outcome(null, VerificationReason.PERMIT_NOT_FOUND);
        }
        if (token.getStatus() == TokenStatus.REVOKED) {
            return new Outcome(permit, VerificationReason.TOKEN_REVOKED);
        }
        if (permit.getStatus() == PermitStatus.CANCELLED) {
            return new Outcome(permit, VerificationReason.PERMIT_CANCELLED);
        }
        if (permit.getStatus() == PermitStatus.EXPIRED || permit.isPastValidity(today)) {
            return new Outcome(permit, VerificationReason.PERMIT_EXPIRED);
        }
        if (permit.getStatus() != PermitStatus.ACTIVE) {
            return new Outcome(permit, VerificationReason.PERMIT_NOT_ACTIVE);
        }
        if (today.isBefore(permit.getValidFrom())) {
            return new Outcome(permit, VerificationReason.PERMIT_NOT_YET_VALID);
        }
        if (context.checkpointAreaId() != null && !context.checkpointAreaId().equals(permit.getArea().getId())) {
            return new Outcome(permit, VerificationReason.AREA_MISMATCH);
        }
        return new Outcome(permit, null);
    }

    private void record(Outcome outcome, ScanContext context, RequestInfo requestInfo, Instant now) {
        var officer = CurrentUser.require();
        VerificationResult result = outcome.valid() ? VerificationResult.VALID : outcome.reason().result();
        BigDecimal lat = locationEnabled ? context.latitude() : null;
        BigDecimal lon = locationEnabled ? context.longitude() : null;
        PermitVerification saved = verifications.save(new PermitVerification(outcome.permit(),
                users.getReferenceById(officer.id()), now, result, outcome.reason(), requestInfo.ipAddress(),
                requestInfo.userAgent(), lat, lon));

        Map<String, Object> meta = new HashMap<>();
        meta.put("verificationId", saved.getId());
        meta.put("result", result.name());
        if (outcome.reason() != null) {
            meta.put("reason", outcome.reason().name());
        }
        audit.record(AuditAction.QR_VERIFIED, "Permit", outcome.permit() == null ? null : outcome.permit().getId(), meta);
        log.info("QR verification result={} reason={} permitId={} officerId={}", result, outcome.reason(),
                outcome.permit() == null ? null : outcome.permit().getId(), officer.id());
    }

    private PermitVerificationResponse toResponse(Outcome outcome, Instant now) {
        Permit p = outcome.permit();
        if (p == null) {
            return PermitVerificationResponse.invalid(outcome.reason(), now);
        }
        if (outcome.reason() == VerificationReason.TOKEN_REVOKED) {
            // A revoked code identifies nothing further.
            return PermitVerificationResponse.invalid(outcome.reason(), now);
        }
        boolean valid = outcome.valid();
        return new PermitVerificationResponse(valid, outcome.reason(),
                valid ? null : outcome.reason().message(),
                effectiveStatus(p, outcome.reason()), p.getPermitNumber(),
                valid ? p.getTourist().getFullName() : null,
                valid ? p.getTourist().getNationality() : null,
                p.getArea().getName(), p.getArea().getCode(), p.getValidFrom(), p.getValidUntil(), now);
    }

    /** Reports EXPIRED for permits past validity even before the nightly expiry job has run. */
    private static PermitStatus effectiveStatus(Permit p, VerificationReason reason) {
        return reason == VerificationReason.PERMIT_EXPIRED ? PermitStatus.EXPIRED : p.getStatus();
    }

    @Transactional(readOnly = true)
    public PageResponse<VerificationLogResponse> history(VerificationResult result, Long permitId, Instant from,
                                                         Instant to, Pageable pageable) {
        return PageResponse.of(verifications.findAll(VerificationSpecifications.filter(result, permitId, from, to),
                        pageable),
                VerificationLogResponse::from);
    }
}
