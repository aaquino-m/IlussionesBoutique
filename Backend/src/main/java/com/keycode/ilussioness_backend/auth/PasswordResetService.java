package com.keycode.ilussioness_backend.auth;

import com.keycode.ilussioness_backend.audit.domain.AuditEntityType;
import com.keycode.ilussioness_backend.audit.domain.AuditEventType;
import com.keycode.ilussioness_backend.audit.domain.AuditOutcome;
import com.keycode.ilussioness_backend.audit.service.AuditService;
import com.keycode.ilussioness_backend.notifications.EmailService;
import com.keycode.ilussioness_backend.services.BusinessValidationException;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Password recovery with stateless, single-use reset links.
 *
 * <p>The reset token is a short-lived JWT signed with a key derived from
 * {@code app.auth.jwt-secret} but different from the session-token key, so a
 * reset token can never be used as a bearer token (and vice versa). It embeds a
 * fingerprint of the current password hash: once the password changes, every
 * previously issued link stops working.</p>
 */
@Service
public class PasswordResetService {

    private static final Logger LOG = LoggerFactory.getLogger(PasswordResetService.class);
    static final String PURPOSE = "password_reset";
    private static final String ISSUER = "ilussiones-password-reset";
    private static final int MIN_PASSWORD_LENGTH = 10;
    private static final int MAX_REQUESTS_PER_WINDOW = 3;
    private static final Duration RATE_WINDOW = Duration.ofMinutes(15);
    private static final int MAX_TRACKED_EMAILS = 10_000;

    private final AccountService accountService;
    private final EmailService emailService;
    private final AuditService auditService;
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final Duration ttl;
    private final String frontendUrl;
    private final boolean logLink;
    private final Map<String, Deque<Instant>> recentRequests = new ConcurrentHashMap<>();

    public PasswordResetService(
            AccountService accountService,
            EmailService emailService,
            AuditService auditService,
            AuthProperties authProperties,
            @Value("${app.auth.password-reset.ttl-minutes:30}") long ttlMinutes,
            @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl,
            @Value("${app.auth.password-reset.log-link:false}") boolean logLink
    ) {
        this.accountService = accountService;
        this.emailService = emailService;
        this.auditService = auditService;
        SecretKey key = DerivedKeys.hmacKey(authProperties.getJwtSecret(), "password-reset-v1");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        this.decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        this.ttl = Duration.ofMinutes(ttlMinutes);
        this.frontendUrl = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
        this.logLink = logLink;
    }

    /**
     * Sends a reset link when the e-mail belongs to an active account. The caller
     * always gets the same answer, so the endpoint does not reveal accounts.
     */
    @Transactional
    public void requestReset(String email) {
        String normalized = AccountService.normalize(email);
        if (!allowRequest(normalized)) {
            return;
        }
        Optional<AccountService.Account> account = accountService.findAccount(normalized);
        account.ifPresent(this::sendResetLink);
        auditService.recordBusinessSuccess(
                AuditEventType.AUTH_PASSWORD_RESET_REQUESTED, AuditEntityType.AUTH, null, Map.of());
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        try {
            requireStrongPassword(newPassword);
            AccountService.Account account = verify(token);
            accountService.changePassword(account, newPassword);
        } catch (BusinessValidationException ex) {
            try {
                auditService.recordBusinessFailure(
                        AuditEventType.AUTH_PASSWORD_RESET_REJECTED, AuditEntityType.AUTH, null,
                        AuditOutcome.REJECTED, Map.of("REASON", "INVALID_DATA"));
            } catch (RuntimeException auditException) {
                LOG.warn("Could not persist password reset rejection ({})",
                        auditException.getClass().getSimpleName());
            }
            throw ex;
        }
        auditService.recordBusinessSuccess(
                AuditEventType.AUTH_PASSWORD_RESET_COMPLETED, AuditEntityType.AUTH, null, Map.of());
    }

    String issueToken(AccountService.Account account) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .id(UUID.randomUUID().toString())
                .subject(account.email())
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .claim("purpose", PURPOSE)
                .claim("fp", fingerprint(account.passwordHash()))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private void sendResetLink(AccountService.Account account) {
        String link = frontendUrl + "/?resetToken=" + issueToken(account);
        String body = """
                Hola %s,

                Recibimos una solicitud para restablecer tu contraseña de Ilussiones Boutique.
                Abre este enlace (válido por %d minutos) para elegir una nueva contraseña:

                %s

                Si no solicitaste el cambio, ignora este mensaje.
                """.formatted(account.name(), ttl.toMinutes(), link);
        boolean sent = emailService.send(account.email(), "Restablecer contraseña - Ilussiones Boutique", body);
        if (!sent && logLink) {
            LOG.info("Password reset link (app.auth.password-reset.log-link=true): {}", link);
        }
    }

    private AccountService.Account verify(String token) {
        Jwt jwt;
        try {
            jwt = decoder.decode(token);
        } catch (JwtException ex) {
            throw new BusinessValidationException("invalid_reset_token");
        }
        if (!PURPOSE.equals(jwt.getClaimAsString("purpose"))
                || !ISSUER.equals(jwt.getClaimAsString("iss"))) {
            throw new BusinessValidationException("invalid_reset_token");
        }
        AccountService.Account account = accountService.findAccount(jwt.getSubject())
                .orElseThrow(() -> new BusinessValidationException("invalid_reset_token"));
        String expected = fingerprint(account.passwordHash());
        String actual = jwt.getClaimAsString("fp");
        if (actual == null || !MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessValidationException("invalid_reset_token");
        }
        return account;
    }

    static void requireStrongPassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH || password.length() > 128
                || password.chars().noneMatch(Character::isLetter)
                || password.chars().noneMatch(Character::isDigit)) {
            throw new BusinessValidationException("weak_password");
        }
    }

    private boolean allowRequest(String email) {
        Instant now = Instant.now();
        if (recentRequests.size() >= MAX_TRACKED_EMAILS && !recentRequests.containsKey(email)) {
            pruneExpired(now);
            if (recentRequests.size() >= MAX_TRACKED_EMAILS) {
                return false;
            }
        }
        Deque<Instant> timestamps = recentRequests.computeIfAbsent(email, ignored -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(now.minus(RATE_WINDOW))) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= MAX_REQUESTS_PER_WINDOW) {
                return false;
            }
            timestamps.addLast(now);
            return true;
        }
    }

    private void pruneExpired(Instant now) {
        Instant cutoff = now.minus(RATE_WINDOW);
        recentRequests.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) {
                Instant last = entry.getValue().peekLast();
                return last == null || last.isBefore(cutoff);
            }
        });
    }

    private static String fingerprint(String passwordHash) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(passwordHash.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
