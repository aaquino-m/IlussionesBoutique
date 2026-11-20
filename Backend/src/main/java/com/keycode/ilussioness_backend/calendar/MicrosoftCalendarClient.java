package com.keycode.ilussioness_backend.calendar;

import com.keycode.ilussioness_backend.models.CalendarProvider;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

/** Microsoft Graph calendar (Outlook / Microsoft 365) of the signed-in user. */
@Component
class MicrosoftCalendarClient implements CalendarClient {

    private static final String LOGIN_URL = "https://login.microsoftonline.com/";
    private static final String GRAPH_URL = "https://graph.microsoft.com/v1.0/me";
    private static final String SCOPES = "offline_access openid email User.Read Calendars.ReadWrite";

    private final CalendarProperties.Client config;
    private final RestClient http = RestClient.create();

    MicrosoftCalendarClient(CalendarProperties properties) {
        this.config = properties.getMicrosoft();
    }

    @Override
    public CalendarProvider provider() {
        return CalendarProvider.MICROSOFT;
    }

    @Override
    public boolean isConfigured() {
        return config.isConfigured();
    }

    private String oauthUrl(String endpoint) {
        return LOGIN_URL + config.getTenant() + "/oauth2/v2.0/" + endpoint;
    }

    @Override
    public String authorizationUrl(String redirectUri, String state) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("client_id", config.getClientId());
        params.put("redirect_uri", redirectUri);
        params.put("response_type", "code");
        params.put("response_mode", "query");
        params.put("scope", SCOPES);
        params.put("prompt", "select_account");
        params.put("state", state);
        return oauthUrl("authorize") + "?" + OAuthSupport.query(params);
    }

    @Override
    public Tokens exchangeCode(String code, String redirectUri) {
        return OAuthSupport.postForTokens(http, oauthUrl("token"), Map.of(
                "code", code,
                "client_id", config.getClientId(),
                "client_secret", config.getClientSecret(),
                "redirect_uri", redirectUri,
                "scope", SCOPES,
                "grant_type", "authorization_code"));
    }

    @Override
    public Tokens refresh(String refreshToken) {
        return OAuthSupport.postForTokens(http, oauthUrl("token"), Map.of(
                "refresh_token", refreshToken,
                "client_id", config.getClientId(),
                "client_secret", config.getClientSecret(),
                "scope", SCOPES,
                "grant_type", "refresh_token"));
    }

    @Override
    @SuppressWarnings("unchecked")
    public String accountEmail(String accessToken) {
        Map<String, Object> me = http.get().uri(GRAPH_URL)
                .headers(h -> h.setBearerAuth(accessToken))
                .retrieve().body(Map.class);
        if (me == null) {
            return null;
        }
        Object mail = me.get("mail");
        return mail != null ? mail.toString() : (String) me.get("userPrincipalName");
    }

    @Override
    @SuppressWarnings("unchecked")
    public String createEvent(String accessToken, Event event) {
        Map<String, Object> created = http.post().uri(GRAPH_URL + "/events")
                .headers(h -> h.setBearerAuth(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body(event))
                .retrieve().body(Map.class);
        return created == null ? null : (String) created.get("id");
    }

    @Override
    public void updateEvent(String accessToken, String eventId, Event event) {
        http.patch().uri(GRAPH_URL + "/events/{id}", eventId)
                .headers(h -> h.setBearerAuth(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body(event))
                .retrieve().toBodilessEntity();
    }

    @Override
    public void deleteEvent(String accessToken, String eventId) {
        http.delete().uri(GRAPH_URL + "/events/{id}", eventId)
                .headers(h -> h.setBearerAuth(accessToken))
                .retrieve().toBodilessEntity();
    }

    private static Map<String, Object> body(Event event) {
        return Map.of(
                "subject", event.title(),
                "body", Map.of("contentType", "text", "content", event.description()),
                "start", Map.of("dateTime", event.start().toString(), "timeZone", event.timeZone()),
                "end", Map.of("dateTime", event.end().toString(), "timeZone", event.timeZone()));
    }
}
