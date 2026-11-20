package com.keycode.ilussioness_backend.calendar;

import com.keycode.ilussioness_backend.models.CalendarProvider;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

/** Google Calendar API v3 (primary calendar). */
@Component
class GoogleCalendarClient implements CalendarClient {

    private static final String AUTHORIZE_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String USERINFO_URL = "https://openidconnect.googleapis.com/v1/userinfo";
    private static final String EVENTS_URL = "https://www.googleapis.com/calendar/v3/calendars/primary/events";
    private static final String SCOPES = "openid email https://www.googleapis.com/auth/calendar.events";

    private final CalendarProperties.Client config;
    private final RestClient http = RestClient.create();

    GoogleCalendarClient(CalendarProperties properties) {
        this.config = properties.getGoogle();
    }

    @Override
    public CalendarProvider provider() {
        return CalendarProvider.GOOGLE;
    }

    @Override
    public boolean isConfigured() {
        return config.isConfigured();
    }

    @Override
    public String authorizationUrl(String redirectUri, String state) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("client_id", config.getClientId());
        params.put("redirect_uri", redirectUri);
        params.put("response_type", "code");
        params.put("scope", SCOPES);
        params.put("access_type", "offline");
        params.put("prompt", "consent");
        params.put("state", state);
        return AUTHORIZE_URL + "?" + OAuthSupport.query(params);
    }

    @Override
    public Tokens exchangeCode(String code, String redirectUri) {
        return OAuthSupport.postForTokens(http, TOKEN_URL, Map.of(
                "code", code,
                "client_id", config.getClientId(),
                "client_secret", config.getClientSecret(),
                "redirect_uri", redirectUri,
                "grant_type", "authorization_code"));
    }

    @Override
    public Tokens refresh(String refreshToken) {
        return OAuthSupport.postForTokens(http, TOKEN_URL, Map.of(
                "refresh_token", refreshToken,
                "client_id", config.getClientId(),
                "client_secret", config.getClientSecret(),
                "grant_type", "refresh_token"));
    }

    @Override
    @SuppressWarnings("unchecked")
    public String accountEmail(String accessToken) {
        Map<String, Object> info = http.get().uri(USERINFO_URL)
                .headers(h -> h.setBearerAuth(accessToken))
                .retrieve().body(Map.class);
        return info == null ? null : (String) info.get("email");
    }

    @Override
    @SuppressWarnings("unchecked")
    public String createEvent(String accessToken, Event event) {
        Map<String, Object> created = http.post().uri(EVENTS_URL)
                .headers(h -> h.setBearerAuth(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body(event))
                .retrieve().body(Map.class);
        return created == null ? null : (String) created.get("id");
    }

    @Override
    public void updateEvent(String accessToken, String eventId, Event event) {
        http.patch().uri(EVENTS_URL + "/{id}", eventId)
                .headers(h -> h.setBearerAuth(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body(event))
                .retrieve().toBodilessEntity();
    }

    @Override
    public void deleteEvent(String accessToken, String eventId) {
        http.delete().uri(EVENTS_URL + "/{id}", eventId)
                .headers(h -> h.setBearerAuth(accessToken))
                .retrieve().toBodilessEntity();
    }

    private static Map<String, Object> body(Event event) {
        return Map.of(
                "summary", event.title(),
                "description", event.description(),
                "start", Map.of("dateTime", event.start().toString(), "timeZone", event.timeZone()),
                "end", Map.of("dateTime", event.end().toString(), "timeZone", event.timeZone()));
    }
}
