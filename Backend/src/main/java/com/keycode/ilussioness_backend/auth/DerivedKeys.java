package com.keycode.ilussioness_backend.auth;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

/**
 * Derives purpose-specific HMAC keys from {@code app.auth.jwt-secret}, so
 * tokens issued for one purpose (password reset, OAuth state) can never be
 * accepted as session tokens.
 */
public final class DerivedKeys {

    private DerivedKeys() {
    }

    public static SecretKey hmacKey(String secret, String purpose) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("app.auth.jwt-secret must be configured");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return new SecretKeySpec(mac.doFinal(purpose.getBytes(StandardCharsets.UTF_8)), "HmacSHA256");
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
