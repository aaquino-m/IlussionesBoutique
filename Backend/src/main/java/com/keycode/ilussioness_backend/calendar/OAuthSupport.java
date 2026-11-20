package com.keycode.ilussioness_backend.calendar;

import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

final class OAuthSupport {

    private OAuthSupport() {
    }

    static String query(Map<String, String> params) {
        return params.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    @SuppressWarnings("unchecked")
    static CalendarClient.Tokens postForTokens(RestClient http, String url, Map<String, String> form) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        form.forEach(body::add);
        Map<String, Object> response = http.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                .body(Map.class);
        if (response == null || response.get("access_token") == null) {
            throw new IllegalStateException("Token endpoint returned no access token");
        }
        Object expires = response.get("expires_in");
        long expiresIn = expires instanceof Number n ? n.longValue()
                : expires == null ? 3600 : Long.parseLong(expires.toString());
        return new CalendarClient.Tokens(
                (String) response.get("access_token"),
                (String) response.get("refresh_token"),
                expiresIn);
    }
}
