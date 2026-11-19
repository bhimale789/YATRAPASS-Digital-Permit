package com.example.touristpermit.account;

import com.example.touristpermit.account.dto.AccountDtos.AccountResponse;
import com.example.touristpermit.account.dto.AccountDtos.ChangePasswordRequest;
import com.example.touristpermit.account.dto.AccountDtos.CodeRequest;
import com.example.touristpermit.account.dto.AccountDtos.RecoveryCodesResponse;
import com.example.touristpermit.auth.dto.LoginResponse;
import com.example.touristpermit.security.mfa.MfaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/account")
@PreAuthorize("isAuthenticated()")
@Tag(name = "Account & Security", description = "Own password and two-factor authentication (all roles)")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    @Operation(summary = "My account and security settings")
    public AccountResponse me() {
        return accountService.me();
    }

    @PostMapping("/password")
    @Operation(summary = "Change my password",
            description = "Signs out all other sessions and returns a new session for this device.")
    public LoginResponse changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        return accountService.changePassword(request);
    }

    @PostMapping("/mfa/setup")
    @Operation(summary = "Start two-factor setup",
            description = "Returns a new secret and QR code for an authenticator app. 2FA turns on only after /mfa/enable.")
    public ResponseEntity<MfaService.SetupResponse> mfaSetup() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(accountService.startMfaSetup());
    }

    @PostMapping("/mfa/enable")
    @Operation(summary = "Confirm and enable two-factor authentication",
            description = "Returns 10 one-time recovery codes, shown only once.")
    public ResponseEntity<RecoveryCodesResponse> mfaEnable(@Valid @RequestBody CodeRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new RecoveryCodesResponse(accountService.enableMfa(request.code())));
    }

    @PostMapping("/mfa/disable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Turn off two-factor authentication (requires a current code or recovery code)")
    public void mfaDisable(@Valid @RequestBody CodeRequest request) {
        accountService.disableMfa(request.code());
    }

    @PostMapping("/mfa/recovery-codes")
    @Operation(summary = "Replace recovery codes (old ones stop working)")
    public ResponseEntity<RecoveryCodesResponse> regenerate(@Valid @RequestBody CodeRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new RecoveryCodesResponse(accountService.regenerateRecoveryCodes(request.code())));
    }
}
