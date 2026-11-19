package com.example.touristpermit.qr;

import com.example.touristpermit.audit.AuditAction;
import com.example.touristpermit.audit.AuditService;
import com.example.touristpermit.common.exception.ConflictException;
import com.example.touristpermit.common.util.SecureTokens;
import com.example.touristpermit.config.AppProperties;
import com.example.touristpermit.permit.Permit;
import com.example.touristpermit.permit.PermitStatus;
import com.example.touristpermit.security.CurrentUser;
import com.example.touristpermit.user.User;
import com.example.touristpermit.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Issues, revokes and renders QR verification tokens.
 * <ul>
 *   <li>Tokens carry 256 bits from {@link java.security.SecureRandom}; never derived from IDs.</li>
 *   <li>Only SHA-256(token) (for lookup) and an AES-GCM ciphertext (for re-rendering) are stored.</li>
 *   <li>The QR encodes {@code <APP_BASE_URL>/verify/<token>} and nothing else.</li>
 * </ul>
 */
@Service
public class QrTokenService {

    public static final int TOKEN_BYTES = 32;
    /** Base64url (no padding) length of a 32-byte token. */
    public static final String TOKEN_PATTERN = "^[A-Za-z0-9_-]{43}$";

    private final VerificationTokenRepository tokens;
    private final TokenCipher cipher;
    private final QrCodeGenerator qrCodeGenerator;
    private final UserRepository users;
    private final AuditService audit;
    private final Clock clock;
    private final String baseUrl;

    public QrTokenService(VerificationTokenRepository tokens, TokenCipher cipher, QrCodeGenerator qrCodeGenerator,
                          UserRepository users, AuditService audit, Clock clock, AppProperties properties) {
        this.tokens = tokens;
        this.cipher = cipher;
        this.qrCodeGenerator = qrCodeGenerator;
        this.users = users;
        this.audit = audit;
        this.clock = clock;
        this.baseUrl = properties.baseUrl().replaceAll("/+$", "");
    }

    /** Issues a fresh ACTIVE token for the permit. Must be called inside the issuing transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void issue(Permit permit) {
        String raw = SecureTokens.randomUrlSafe(TOKEN_BYTES);
        tokens.save(new VerificationToken(permit, SecureTokens.sha256Hex(raw), cipher.encrypt(raw, permit.getId()),
                currentUserRef(), clock.instant()));
        audit.record(AuditAction.QR_GENERATED, "Permit", permit.getId(), Map.of("permitNumber", permit.getPermitNumber()));
    }

    /** Revokes the permit's active token (if any). Returns true if one was revoked. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean revokeActive(Permit permit, String reason) {
        return tokens.findByPermitIdAndStatus(permit.getId(), TokenStatus.ACTIVE)
                .map(token -> {
                    token.revoke(currentUserRef(), clock.instant(), reason);
                    // Flush so the partial unique index allows a new ACTIVE token in the same transaction.
                    tokens.saveAndFlush(token);
                    audit.record(AuditAction.QR_REVOKED, "Permit", permit.getId(), Map.of(
                            "permitNumber", permit.getPermitNumber(), "tokenId", token.getId()));
                    return true;
                })
                .orElse(false);
    }

    /** Revokes the current token and issues a new one. Only for ACTIVE permits. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void regenerate(Permit permit, String reason) {
        if (permit.getStatus() != PermitStatus.ACTIVE) {
            throw new ConflictException("QR codes can only be regenerated for ACTIVE permits");
        }
        revokeActive(permit, reason);
        issue(permit);
    }

    /** PNG image of the permit's active QR code. */
    @Transactional(readOnly = true)
    public byte[] activeQrPng(Permit permit) {
        VerificationToken token = tokens.findByPermitIdAndStatus(permit.getId(), TokenStatus.ACTIVE)
                .orElseThrow(() -> new ConflictException("This permit has no active QR code"));
        String raw = cipher.decrypt(token.getTokenCiphertext(), permit.getId());
        return qrCodeGenerator.png(verificationUrl(raw));
    }

    public String verificationUrl(String rawToken) {
        return baseUrl + "/verify/" + rawToken;
    }

    @Transactional(readOnly = true)
    public boolean hasActiveToken(Long permitId) {
        return tokens.findByPermitIdAndStatus(permitId, TokenStatus.ACTIVE).isPresent();
    }

    @Transactional(readOnly = true)
    public Set<Long> permitIdsWithActiveToken(Collection<Long> permitIds) {
        return permitIds.isEmpty() ? Set.of() : tokens.findPermitIdsWithActiveToken(permitIds);
    }

    private User currentUserRef() {
        return CurrentUser.optional().map(u -> users.getReferenceById(u.id())).orElse(null);
    }
}
