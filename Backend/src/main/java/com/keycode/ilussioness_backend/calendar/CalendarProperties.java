package com.keycode.ilussioness_backend.calendar;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OAuth client registrations and sync options for external calendars
 * ({@code app.calendar.*}). A provider is offered only when its client id and
 * secret are configured.
 */
@ConfigurationProperties(prefix = "app.calendar")
public class CalendarProperties {

    /** Public base URL of this backend; OAuth redirect URIs are built from it. */
    private String redirectBaseUrl = "http://localhost:8080";
    private String timeZone = "America/El_Salvador";
    private int eventDurationMinutes = 60;
    /** Secret used to encrypt stored OAuth tokens. */
    private String tokenEncryptionKey;
    private final Client google = new Client();
    private final Client microsoft = new Client();

    public static class Client {
        private String clientId;
        private String clientSecret;
        /** Microsoft only: tenant id, {@code common} or {@code consumers}. */
        private String tenant = "common";

        public boolean isConfigured() {
            return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        public String getTenant() {
            return tenant;
        }

        public void setTenant(String tenant) {
            this.tenant = tenant;
        }
    }

    public String getRedirectBaseUrl() {
        return redirectBaseUrl;
    }

    public void setRedirectBaseUrl(String redirectBaseUrl) {
        this.redirectBaseUrl = redirectBaseUrl;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }

    public int getEventDurationMinutes() {
        return eventDurationMinutes;
    }

    public void setEventDurationMinutes(int eventDurationMinutes) {
        this.eventDurationMinutes = eventDurationMinutes;
    }

    public String getTokenEncryptionKey() {
        return tokenEncryptionKey;
    }

    public void setTokenEncryptionKey(String tokenEncryptionKey) {
        this.tokenEncryptionKey = tokenEncryptionKey;
    }

    public Client getGoogle() {
        return google;
    }

    public Client getMicrosoft() {
        return microsoft;
    }
}
