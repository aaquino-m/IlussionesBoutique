package com.keycode.ilussioness_backend.calendar;

import com.keycode.ilussioness_backend.models.CalendarProvider;

import java.time.LocalDateTime;

/** Provider-specific OAuth and event operations. */
interface CalendarClient {

    record Tokens(String accessToken, String refreshToken, long expiresInSeconds) {
    }

    record Event(String title, String description, LocalDateTime start, LocalDateTime end, String timeZone) {
    }

    CalendarProvider provider();

    boolean isConfigured();

    String authorizationUrl(String redirectUri, String state);

    Tokens exchangeCode(String code, String redirectUri);

    Tokens refresh(String refreshToken);

    String accountEmail(String accessToken);

    String createEvent(String accessToken, Event event);

    void updateEvent(String accessToken, String eventId, Event event);

    void deleteEvent(String accessToken, String eventId);
}
