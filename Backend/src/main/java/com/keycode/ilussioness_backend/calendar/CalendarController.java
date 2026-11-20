package com.keycode.ilussioness_backend.calendar;

import com.keycode.ilussioness_backend.models.CalendarProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/calendar")
@CrossOrigin(origins = "*")
public class CalendarController {

    private static final Logger LOG = LoggerFactory.getLogger(CalendarController.class);

    private final CalendarService calendarService;
    private final String frontendUrl;

    public CalendarController(
            CalendarService calendarService,
            @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl
    ) {
        this.calendarService = calendarService;
        this.frontendUrl = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
    }

    @GetMapping("/status")
    public List<CalendarService.ProviderStatus> status() {
        return calendarService.status();
    }

    @GetMapping("/{provider}/authorize")
    public Map<String, String> authorize(@PathVariable String provider) {
        return Map.of("url", calendarService.authorizationUrl(CalendarService.parseProvider(provider)));
    }

    /** OAuth redirect target. Always sends the browser back to the frontend. */
    @GetMapping("/{provider}/callback")
    public ResponseEntity<Void> callback(
            @PathVariable String provider,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error
    ) {
        String result = "error";
        String providerName = provider.toLowerCase(Locale.ROOT);
        try {
            CalendarProvider parsed = CalendarService.parseProvider(provider);
            providerName = parsed.name().toLowerCase(Locale.ROOT);
            if (error == null && code != null && state != null) {
                calendarService.completeAuthorization(parsed, code, state);
                result = "connected";
            }
        } catch (RuntimeException ex) {
            LOG.warn("Calendar OAuth callback failed ({})", ex.getClass().getSimpleName());
        }
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(frontendUrl + "/?calendar=" + result + "&provider=" + providerName))
                .build();
    }

    @DeleteMapping("/{provider}")
    public ResponseEntity<Void> disconnect(@PathVariable String provider) {
        calendarService.disconnect(CalendarService.parseProvider(provider));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sync")
    public Map<String, Integer> sync() {
        return Map.of("synced", calendarService.syncUpcoming());
    }
}
