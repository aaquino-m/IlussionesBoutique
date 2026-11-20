package com.keycode.ilussioness_backend.auth;

import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

/**
 * Demo authentication endpoint.
 *
 * <p>The backend is the only authority that decides whether a login is valid.
 * Invalid email and wrong password produce the same generic {@code 401} body so
 * the endpoint does not disclose whether an account exists.</p>
 */
@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final AccountService accountService;
    private final JwtTokenService jwtTokenService;
    private final PasswordResetService passwordResetService;

    public AuthController(
            AccountService accountService,
            JwtTokenService jwtTokenService,
            PasswordResetService passwordResetService
    ) {
        this.accountService = accountService;
        this.jwtTokenService = jwtTokenService;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        Optional<AuthenticatedUser> user = accountService.authenticate(
                request.email(), request.password());

        if (user.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .cacheControl(CacheControl.noStore())
                    .body(Map.of("error", "invalid_credentials"));
        }

        JwtTokenService.IssuedToken token = jwtTokenService.issue(user.get());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(LoginResponse.from(token, user.get()));
    }

    /**
     * Always answers {@code 202} with the same body, whether or not the e-mail
     * belongs to an account.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestReset(request.email());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .cacheControl(CacheControl.noStore())
                .body(Map.of("status", "accepted"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
