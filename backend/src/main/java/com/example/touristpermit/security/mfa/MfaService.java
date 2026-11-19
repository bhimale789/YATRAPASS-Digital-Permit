package com.example.touristpermit.security.mfa;

import com.example.touristpermit.audit.AuditAction;
import com.example.touristpermit.audit.AuditService;
import com.example.touristpermit.common.exception.AuthenticationFailedException;
import com.example.touristpermit.common.exception.BusinessRuleException;
import com.example.touristpermit.common.exception.ConflictException;
import com.example.touristpermit.common.util.SecureTokens;
import com.example.touristpermit.qr.QrCodeGenerator;
import com.example.touristpermit.qr.TokenCipher;
import com.example.touristpermit.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;

/**
 * Two-factor authentication with TOTP authenticator apps plus one-time recovery codes.
 * Secrets are stored AES-GCM encrypted and bound to the user id; recovery codes are
 * stored as SHA-256 hashes and shown to the user exactly once.
 */
@Service
public class MfaService {

    public static final String ISSUER = "YatraPass";
    static final int RECOVERY_CODE_COUNT = 10;
    private static final char[] RECOVERY_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    public record SetupResponse(String secret, String otpauthUri, String qrPngDataUrl) {
    }

    private final TokenCipher cipher;
    private final MfaRecoveryCodeRepository recoveryCodes;
    private final QrCodeGenerator qrCodes;
    private final AuditService audit;
    private final Clock clock;

    public MfaService(TokenCipher cipher, MfaRecoveryCodeRepository recoveryCodes, QrCodeGenerator qrCodes,
                      AuditService audit, Clock clock) {
        this.cipher = cipher;
        this.recoveryCodes = recoveryCodes;
        this.qrCodes = qrCodes;
        this.audit = audit;
        this.clock = clock;
    }

    /** Generates a new (pending) secret. 2FA stays off until {@link #enable} confirms a code. */
    @Transactional(propagation = Propagation.MANDATORY)
    public SetupResponse startSetup(User user) {
        if (user.isTotpEnabled()) {
            throw new ConflictException("Two-factor authentication is already enabled");
        }
        String secret = Totp.newSecret();
        user.setPendingTotpSecret(cipher.encrypt(secret, context(user)));
        String uri = Totp.otpauthUri(ISSUER, user.getEmail(), secret);
        String qr = "data:image/png;base64," + Base64.getEncoder().encodeToString(qrCodes.png(uri));
        return new SetupResponse(secret, uri, qr);
    }

    /** Confirms the pending secret with a code from the app; returns new recovery codes (shown once). */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<String> enable(User user, String code) {
        if (user.isTotpEnabled()) {
            throw new ConflictException("Two-factor authentication is already enabled");
        }
        if (user.getTotpSecret() == null) {
            throw new BusinessRuleException("Start two-factor setup first");
        }
        OptionalLong step = Totp.verify(secret(user), normalizeTotp(code), clock.instant(), null);
        if (step.isEmpty()) {
            throw new BusinessRuleException("The code is incorrect. Check the time on your phone and try again.");
        }
        user.enableTotp(step.getAsLong());
        List<String> codes = replaceRecoveryCodes(user);
        audit.record(AuditAction.MFA_ENABLED, "User", user.getId(), Map.of());
        return codes;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void disable(User user, String code) {
        requireEnabled(user);
        if (!verifySecondFactor(user, code)) {
            throw new BusinessRuleException("The code is incorrect");
        }
        clear(user);
        audit.record(AuditAction.MFA_DISABLED, "User", user.getId(), Map.of());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<String> regenerateRecoveryCodes(User user, String code) {
        requireEnabled(user);
        if (!verifySecondFactor(user, code)) {
            throw new BusinessRuleException("The code is incorrect");
        }
        List<String> codes = replaceRecoveryCodes(user);
        audit.record(AuditAction.MFA_RECOVERY_CODES_REGENERATED, "User", user.getId(), Map.of());
        return codes;
    }

    /** Administrator reset when a staff member lost their device. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reset(User user) {
        clear(user);
    }

    /**
     * Verifies a login second factor: a current TOTP code (not previously used) or an
     * unused recovery code, which is then consumed.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean verifySecondFactor(User user, String code) {
        if (code == null || !user.isTotpEnabled()) {
            return false;
        }
        String trimmed = code.trim();
        OptionalLong step = Totp.verify(secret(user), normalizeTotp(trimmed), clock.instant(), user.getTotpLastStep());
        if (step.isPresent()) {
            user.setTotpLastStep(step.getAsLong());
            return true;
        }
        String normalized = normalizeRecovery(trimmed);
        if (normalized.length() != 10) {
            return false;
        }
        return recoveryCodes.findUnusedForUpdate(user.getId(), SecureTokens.sha256Hex(normalized))
                .map(c -> {
                    c.markUsed(clock.instant());
                    audit.record(user.getId(), user.getRole().name(), AuditAction.MFA_RECOVERY_CODE_USED, "User",
                            user.getId(), Map.of("remaining", recoveryCodes.countUnused(user.getId()) - 1));
                    return true;
                })
                .orElse(false);
    }

    public long remainingRecoveryCodes(User user) {
        return user.isTotpEnabled() ? recoveryCodes.countUnused(user.getId()) : 0;
    }

    private List<String> replaceRecoveryCodes(User user) {
        recoveryCodes.deleteAllForUser(user.getId());
        List<String> codes = new ArrayList<>(RECOVERY_CODE_COUNT);
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            StringBuilder raw = new StringBuilder(10);
            for (int j = 0; j < 10; j++) {
                raw.append(RECOVERY_ALPHABET[RANDOM.nextInt(RECOVERY_ALPHABET.length)]);
            }
            recoveryCodes.save(new MfaRecoveryCode(user, SecureTokens.sha256Hex(raw.toString())));
            codes.add(raw.substring(0, 5) + "-" + raw.substring(5));
        }
        return codes;
    }

    private void clear(User user) {
        user.disableTotp();
        recoveryCodes.deleteAllForUser(user.getId());
    }

    private void requireEnabled(User user) {
        if (!user.isTotpEnabled()) {
            throw new ConflictException("Two-factor authentication is not enabled");
        }
    }

    private String secret(User user) {
        try {
            return cipher.decrypt(user.getTotpSecret(), context(user));
        } catch (IllegalStateException e) {
            throw new AuthenticationFailedException("Two-factor configuration is invalid; ask an administrator to reset it");
        }
    }

    private static String context(User user) {
        return "totp:" + user.getId();
    }

    private static String normalizeTotp(String code) {
        return code == null ? null : code.replace(" ", "").trim();
    }

    private static String normalizeRecovery(String code) {
        return code.replace("-", "").replace(" ", "").toUpperCase(Locale.ROOT);
    }
}
