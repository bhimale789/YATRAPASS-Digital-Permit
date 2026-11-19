package com.example.touristpermit.auth;

import com.example.touristpermit.audit.AuditAction;
import com.example.touristpermit.audit.AuditService;
import com.example.touristpermit.auth.dto.LoginRequest;
import com.example.touristpermit.auth.dto.LoginResponse;
import com.example.touristpermit.auth.dto.MfaLoginRequest;
import com.example.touristpermit.auth.dto.RegisterRequest;
import com.example.touristpermit.common.exception.AuthenticationFailedException;
import com.example.touristpermit.common.exception.ConflictException;
import com.example.touristpermit.common.exception.TooManyRequestsException;
import com.example.touristpermit.common.util.SecureTokens;
import com.example.touristpermit.config.AppProperties;
import com.example.touristpermit.security.JwtService;
import com.example.touristpermit.security.LoginRateLimiter;
import com.example.touristpermit.security.mfa.MfaService;
import com.example.touristpermit.tourist.TouristProfile;
import com.example.touristpermit.tourist.TouristProfileRepository;
import com.example.touristpermit.user.Role;
import com.example.touristpermit.user.User;
import com.example.touristpermit.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final int REFRESH_TOKEN_BYTES = 32;
    private static final String INVALID_CREDENTIALS = "Invalid email or password";

    private final UserRepository users;
    private final TouristProfileRepository profiles;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginRateLimiter rateLimiter;
    private final AuditService audit;
    private final MfaService mfaService;
    private final Duration refreshTtl;
    private final Clock clock;
    /** Hash used to equalize timing when the account does not exist. */
    private final String dummyHash;

    public AuthService(UserRepository users, TouristProfileRepository profiles, RefreshTokenRepository refreshTokens,
                       PasswordEncoder passwordEncoder, JwtService jwtService, LoginRateLimiter rateLimiter,
                       AuditService audit, MfaService mfaService, AppProperties properties, Clock clock) {
        this.users = users;
        this.profiles = profiles;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
        this.mfaService = mfaService;
        this.refreshTtl = properties.security().jwt().refreshTokenTtl();
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode(SecureTokens.randomUrlSafe(16));
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional
    public LoginResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw new ConflictException("An account with this email already exists");
        }
        User user = users.save(new User(email, passwordEncoder.encode(request.password()), Role.TOURIST));
        profiles.save(new TouristProfile(user, request.firstName().trim(), request.lastName().trim()));
        audit.record(user.getId(), Role.TOURIST.name(), AuditAction.USER_REGISTERED, "User", user.getId(), Map.of());
        log.info("Registered tourist userId={}", user.getId());
        return issueTokens(user);
    }

    @Transactional
    public LoginResponse login(LoginRequest request, String ipAddress) {
        String email = normalizeEmail(request.email());
        if (rateLimiter.isBlocked(ipAddress, email)) {
            throw new TooManyRequestsException("Too many failed login attempts. Please try again later.");
        }
        User user = users.findByEmail(email).orElse(null);
        // Always run BCrypt to avoid revealing whether the account exists via response timing.
        boolean passwordMatches = passwordEncoder.matches(request.password(),
                user != null ? user.getPasswordHash() : dummyHash);
        if (user == null || !passwordMatches || !user.isEnabled()) {
            rateLimiter.recordFailure(ipAddress, email);
            log.info("Failed login attempt from ip={}", ipAddress);
            throw new AuthenticationFailedException(INVALID_CREDENTIALS);
        }
        rateLimiter.recordSuccess(email);
        if (user.isTotpEnabled()) {
            // Password is correct; the session is only issued after the second factor.
            return LoginResponse.mfaChallenge(jwtService.issueMfaToken(user), userInfo(user));
        }
        return completeLogin(user);
    }

    /**
     * Second step of a two-factor login: a TOTP code or a one-time recovery code.
     * Failures are rate limited per account and audited.
     */
    @Transactional(noRollbackFor = {AuthenticationFailedException.class, TooManyRequestsException.class})
    public LoginResponse loginWithMfa(MfaLoginRequest request) {
        Long userId = jwtService.parseMfaToken(request.mfaToken())
                .orElseThrow(() -> new AuthenticationFailedException("Your sign-in has expired. Please log in again."));
        User user = users.findById(userId).filter(User::isEnabled)
                .orElseThrow(() -> new AuthenticationFailedException(INVALID_CREDENTIALS));
        if (rateLimiter.isMfaBlocked(userId)) {
            throw new TooManyRequestsException("Too many incorrect codes. Please try again later.");
        }
        if (!mfaService.verifySecondFactor(user, request.code())) {
            rateLimiter.recordMfaFailure(userId);
            audit.record(user.getId(), user.getRole().name(), AuditAction.LOGIN_MFA_FAILED, "User", user.getId(), Map.of());
            throw new AuthenticationFailedException("The code is incorrect");
        }
        rateLimiter.recordMfaSuccess(userId);
        return completeLogin(user);
    }

    private LoginResponse completeLogin(User user) {
        user.setLastLoginAt(clock.instant());
        if (user.getRole() != Role.TOURIST) {
            audit.record(user.getId(), user.getRole().name(), AuditAction.ADMIN_LOGIN, "User", user.getId(),
                    Map.of("mfa", user.isTotpEnabled()));
        }
        return issueTokens(user);
    }

    private static LoginResponse.UserInfo userInfo(User user) {
        return new LoginResponse.UserInfo(user.getId(), user.getEmail(), user.getRole());
    }

    /**
     * Rotates a refresh token. Presenting an already-revoked token indicates theft or
     * replay, so every session of that user is revoked.
     */
    @Transactional(noRollbackFor = AuthenticationFailedException.class)
    public LoginResponse refresh(String rawRefreshToken) {
        Instant now = clock.instant();
        RefreshToken token = refreshTokens.findByTokenHash(SecureTokens.sha256Hex(rawRefreshToken))
                .orElseThrow(() -> new AuthenticationFailedException("Invalid refresh token"));
        if (token.getRevokedAt() != null) {
            refreshTokens.revokeAllForUser(token.getUser().getId(), now);
            log.warn("Revoked refresh token reuse detected for userId={}; all sessions revoked", token.getUser().getId());
            throw new AuthenticationFailedException("Invalid refresh token");
        }
        if (!token.isUsable(now) || !token.getUser().isEnabled()) {
            throw new AuthenticationFailedException("Invalid refresh token");
        }
        token.revoke(now);
        return issueTokens(token.getUser());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokens.findByTokenHash(SecureTokens.sha256Hex(rawRefreshToken))
                .ifPresent(t -> t.revoke(clock.instant()));
    }

    /** Starts a new session (access + refresh token) for the user. */
    public LoginResponse issueTokens(User user) {
        String rawRefresh = SecureTokens.randomUrlSafe(REFRESH_TOKEN_BYTES);
        refreshTokens.save(new RefreshToken(user, SecureTokens.sha256Hex(rawRefresh), clock.instant().plus(refreshTtl)));
        return LoginResponse.session(jwtService.issueAccessToken(user), rawRefresh, jwtService.accessTokenTtlSeconds(),
                userInfo(user), user.isMustChangePassword());
    }

    @Scheduled(cron = "0 30 3 * * *")
    @SchedulerLock(name = "purgeExpiredRefreshTokens")
    @Transactional
    public void purgeExpiredRefreshTokens() {
        int removed = refreshTokens.deleteExpiredBefore(clock.instant().minus(Duration.ofDays(1)));
        if (removed > 0) {
            log.info("Purged {} expired refresh tokens", removed);
        }
    }
}
