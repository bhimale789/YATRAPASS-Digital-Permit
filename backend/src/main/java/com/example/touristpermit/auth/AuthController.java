package com.example.touristpermit.auth;

import com.example.touristpermit.auth.dto.LoginRequest;
import com.example.touristpermit.auth.dto.LoginResponse;
import com.example.touristpermit.auth.dto.MfaLoginRequest;
import com.example.touristpermit.auth.dto.RefreshRequest;
import com.example.touristpermit.auth.dto.RegisterRequest;
import com.example.touristpermit.common.util.RequestInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Registration, login and token management")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a tourist account", description = "Creates a TOURIST user and returns tokens.")
    public LoginResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    @Operation(summary = "Log in", description = "Returns an access token and a single-use refresh token. "
            + "Repeated failures are rate limited (HTTP 429).")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return authService.login(request, RequestInfo.from(http).ipAddress());
    }

    @PostMapping("/login/mfa")
    @Operation(summary = "Complete a two-factor login",
            description = "Send the mfaToken from /auth/login with a 6-digit authenticator code or a recovery code.")
    public LoginResponse loginMfa(@Valid @RequestBody MfaLoginRequest request) {
        return authService.loginWithMfa(request);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate tokens", description = "Exchanges a refresh token for a new token pair. "
            + "The presented refresh token is revoked.")
    public LoginResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Log out", description = "Revokes the given refresh token. Idempotent.")
    public void logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
    }
}
