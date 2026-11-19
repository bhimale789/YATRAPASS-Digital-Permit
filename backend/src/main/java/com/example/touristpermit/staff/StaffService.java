package com.example.touristpermit.staff;

import com.example.touristpermit.audit.AuditAction;
import com.example.touristpermit.audit.AuditService;
import com.example.touristpermit.auth.AuthService;
import com.example.touristpermit.auth.RefreshTokenRepository;
import com.example.touristpermit.common.exception.BusinessRuleException;
import com.example.touristpermit.common.exception.ConflictException;
import com.example.touristpermit.common.exception.ResourceNotFoundException;
import com.example.touristpermit.common.response.PageResponse;
import com.example.touristpermit.common.util.SearchPatterns;
import com.example.touristpermit.security.CurrentUser;
import com.example.touristpermit.security.mfa.MfaService;
import com.example.touristpermit.staff.dto.StaffDtos.CreateStaffRequest;
import com.example.touristpermit.staff.dto.StaffDtos.StaffCredentialsResponse;
import com.example.touristpermit.staff.dto.StaffDtos.StaffResponse;
import com.example.touristpermit.staff.dto.StaffDtos.UpdateStaffRequest;
import com.example.touristpermit.user.Role;
import com.example.touristpermit.user.User;
import com.example.touristpermit.user.UserRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Administrator management of staff accounts (ADMIN / OFFICER). Tourists are managed on
 * the Tourists page. Guards prevent an administrator from locking themselves out or
 * removing the last active administrator.
 */
@Service
public class StaffService {

    private static final Set<Role> STAFF_ROLES = EnumSet.of(Role.ADMIN, Role.OFFICER);
    private static final char[] PASSWORD_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokens;
    private final MfaService mfaService;
    private final AuditService audit;
    private final Clock clock;

    public StaffService(UserRepository users, PasswordEncoder passwordEncoder, RefreshTokenRepository refreshTokens,
                        MfaService mfaService, AuditService audit, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokens = refreshTokens;
        this.mfaService = mfaService;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<StaffResponse> search(String query, Role role, Pageable pageable) {
        Specification<User> spec = (root, q, cb) -> root.get("role").in(role != null && STAFF_ROLES.contains(role)
                ? Set.of(role) : STAFF_ROLES);
        String like = SearchPatterns.contains(query);
        if (like != null) {
            spec = spec.and((root, q, cb) -> cb.or(
                    cb.like(cb.lower(root.get("email")), like, '\\'),
                    cb.like(cb.lower(cb.coalesce(root.get("displayName"), "")), like, '\\')));
        }
        return PageResponse.of(users.findAll(spec, pageable), StaffResponse::from);
    }

    @Transactional
    public StaffCredentialsResponse create(CreateStaffRequest request) {
        requireStaffRole(request.role());
        String email = AuthService.normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw new ConflictException("An account with this email already exists");
        }
        String temporary = temporaryPassword();
        User user = new User(email, passwordEncoder.encode(temporary), request.role());
        user.setDisplayName(request.displayName().trim());
        user.setMustChangePassword(true);
        user = users.save(user);
        audit.record(AuditAction.STAFF_CREATED, "User", user.getId(), Map.of("role", user.getRole().name()));
        return new StaffCredentialsResponse(StaffResponse.from(user), temporary);
    }

    @Transactional
    public StaffResponse update(Long id, UpdateStaffRequest request) {
        requireStaffRole(request.role());
        User user = find(id);
        if (user.getRole() != request.role()) {
            if (isSelf(user)) {
                throw new BusinessRuleException("You cannot change your own role");
            }
            if (user.getRole() == Role.ADMIN) {
                requireAnotherActiveAdmin(user);
            }
            user.setRole(request.role());
            refreshTokens.revokeAllForUser(user.getId(), clock.instant()); // new role applies on next sign-in
        }
        user.setDisplayName(request.displayName().trim());
        audit.record(AuditAction.STAFF_UPDATED, "User", id, Map.of("role", user.getRole().name()));
        return StaffResponse.from(user);
    }

    @Transactional
    public StaffResponse setEnabled(Long id, boolean enabled) {
        User user = find(id);
        if (!enabled) {
            if (isSelf(user)) {
                throw new BusinessRuleException("You cannot disable your own account");
            }
            if (user.getRole() == Role.ADMIN) {
                requireAnotherActiveAdmin(user);
            }
            refreshTokens.revokeAllForUser(user.getId(), clock.instant());
        }
        user.setEnabled(enabled);
        audit.record(AuditAction.STAFF_UPDATED, "User", id, Map.of("enabled", enabled));
        return StaffResponse.from(user);
    }

    @Transactional
    public StaffCredentialsResponse resetPassword(Long id) {
        User user = find(id);
        if (isSelf(user)) {
            throw new BusinessRuleException("Use 'Change password' on your Security page instead");
        }
        String temporary = temporaryPassword();
        user.setPasswordHash(passwordEncoder.encode(temporary));
        user.setMustChangePassword(true);
        refreshTokens.revokeAllForUser(user.getId(), clock.instant());
        audit.record(AuditAction.STAFF_PASSWORD_RESET, "User", id, Map.of());
        return new StaffCredentialsResponse(StaffResponse.from(user), temporary);
    }

    /** Clears two-factor authentication for a staff member who lost their device. */
    @Transactional
    public StaffResponse resetMfa(Long id) {
        User user = find(id);
        if (isSelf(user)) {
            throw new BusinessRuleException("Turn off two-factor authentication from your Security page instead");
        }
        if (!user.isTotpEnabled() && user.getTotpSecret() == null) {
            throw new ConflictException("Two-factor authentication is not set up for this account");
        }
        mfaService.reset(user);
        refreshTokens.revokeAllForUser(user.getId(), clock.instant());
        audit.record(AuditAction.MFA_RESET, "User", id, Map.of());
        return StaffResponse.from(user);
    }

    private void requireAnotherActiveAdmin(User target) {
        if (target.isEnabled() && users.countByRoleAndEnabledTrue(Role.ADMIN) <= 1) {
            throw new BusinessRuleException("At least one active administrator must remain");
        }
    }

    private static void requireStaffRole(Role role) {
        if (!STAFF_ROLES.contains(role)) {
            throw new BusinessRuleException("Staff role must be ADMIN or OFFICER");
        }
    }

    private static boolean isSelf(User user) {
        return user.getId().equals(CurrentUser.require().id());
    }

    private User find(Long id) {
        return users.findById(id).filter(u -> STAFF_ROLES.contains(u.getRole()))
                .orElseThrow(() -> new ResourceNotFoundException("Staff account not found"));
    }

    /** 16 characters from an unambiguous alphabet, with at least one letter and one digit. */
    static String temporaryPassword() {
        while (true) {
            StringBuilder sb = new StringBuilder(16);
            for (int i = 0; i < 16; i++) {
                sb.append(PASSWORD_ALPHABET[RANDOM.nextInt(PASSWORD_ALPHABET.length)]);
            }
            String candidate = sb.toString();
            if (candidate.chars().anyMatch(Character::isDigit) && candidate.chars().anyMatch(Character::isLetter)) {
                return candidate;
            }
        }
    }
}
