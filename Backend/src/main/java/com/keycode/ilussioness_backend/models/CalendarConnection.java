package com.keycode.ilussioness_backend.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * OAuth connection of the boutique to an external calendar. Tokens are stored
 * encrypted (AES-GCM) and are never serialised to API clients.
 */
@Entity
@Table(name = "calendar_connection")
public class CalendarConnection {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", length = 20)
    private CalendarProvider provider;

    @Column(name = "account_email", length = 200)
    private String accountEmail;

    @Column(name = "access_token_enc", nullable = false, columnDefinition = "text")
    private String accessTokenEncrypted;

    @Column(name = "refresh_token_enc", columnDefinition = "text")
    private String refreshTokenEncrypted;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "connected_at", nullable = false)
    private Instant connectedAt;

    public CalendarProvider getProvider() {
        return provider;
    }

    public void setProvider(CalendarProvider provider) {
        this.provider = provider;
    }

    public String getAccountEmail() {
        return accountEmail;
    }

    public void setAccountEmail(String accountEmail) {
        this.accountEmail = accountEmail;
    }

    public String getAccessTokenEncrypted() {
        return accessTokenEncrypted;
    }

    public void setAccessTokenEncrypted(String accessTokenEncrypted) {
        this.accessTokenEncrypted = accessTokenEncrypted;
    }

    public String getRefreshTokenEncrypted() {
        return refreshTokenEncrypted;
    }

    public void setRefreshTokenEncrypted(String refreshTokenEncrypted) {
        this.refreshTokenEncrypted = refreshTokenEncrypted;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getConnectedAt() {
        return connectedAt;
    }

    public void setConnectedAt(Instant connectedAt) {
        this.connectedAt = connectedAt;
    }
}
