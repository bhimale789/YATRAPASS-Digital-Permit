package com.example.touristpermit.account;

import com.example.touristpermit.account.dto.AccountDtos.AccountResponse;
import com.example.touristpermit.account.dto.AccountDtos.ChangePasswordRequest;
import com.example.touristpermit.audit.AuditAction;
import com.example.touristpermit.audit.AuditService;
import com.example.touristpermit.auth.AuthService;
import com.example.touristpermit.auth.RefreshTokenRepository;
import com.example.touristpermit.auth.dto.LoginResponse;
import com.example.touristpermit.common.exception.BusinessRuleException;
import com.example.touristpermit.common.exception.ResourceNotFoundException;
import com.example.touristpermit.security.CurrentUser;
import com.example.touristpermit.security.mfa.MfaService;
import com.example.touristpermit.user.Role;
import com.example.touristpermit.user.User;
import com.example.touristpermit.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Map;

/** The signed-in user's own account: password and two-factor authentication. */
@Service
public class AccountService {

    /** Staff accounts protect sensitive data, so they need longer passwords. */
    public static final int STAFF_MIN_PASSWORD_LENGTH = 12;

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokens;
    private final AuthService authService;
    private final MfaService mfaService;
    private final AuditService audit;
    private final Clock clock;

    public AccountService(UserRepository users, PasswordEncoder passwordEncoder, RefreshTokenRepository refreshTokens,
                          AuthService authService, MfaService mfaService, AuditService audit, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokens = refreshTokens;
        this.authService = authService;
        this.mfaService = mfaService;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AccountResponse me() {
        return toResponse(current());
    }

    /**
     * Changes the password, signs out every other session and returns a fresh session for
     * this device. Clears the "must change password" flag set by an administrator.
     */
    @Transactional
    public LoginResponse changePassword(ChangePasswordRequest request) {
        User user = current();
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessRuleException("Current password is incorrect");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BusinessRuleException("The new password must be different from the current one");
        }
        if (user.getRole() != Role.TOURIST && request.newPassword().length() < STAFF_MIN_PASSWORD_LENGTH) {
            throw new BusinessRuleException("Staff passwords must be at least " + STAFF_MIN_PASSWORD_LENGTH + " characters");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        refreshTokens.revokeAllForUser(user.getId(), clock.instant());
        audit.record(AuditAction.PASSWORD_CHANGED, "User", user.getId(), Map.of());
        return authService.issueTokens(user);
    }

    @Transactional
    public MfaService.SetupResponse startMfaSetup() {
        return mfaService.startSetup(current());
    }

    @Transactional
    public List<String> enableMfa(String code) {
        return mfaService.enable(current(), code);
    }

    @Transactional
    public void disableMfa(String code) {
        mfaService.disable(current(), code);
    }

    @Transactional
    public List<String> regenerateRecoveryCodes(String code) {
        return mfaService.regenerateRecoveryCodes(current(), code);
    }

    private User current() {
        return users.findById(CurrentUser.require().id())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
    }

    private AccountResponse toResponse(User u) {
        return new AccountResponse(u.getId(), u.getEmail(), u.getRole(), u.getDisplayName(), u.isTotpEnabled(),
                mfaService.remainingRecoveryCodes(u), u.isMustChangePassword(), u.getLastLoginAt());
    }
}
