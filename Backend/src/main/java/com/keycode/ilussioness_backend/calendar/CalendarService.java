package com.keycode.ilussioness_backend.calendar;

import com.keycode.ilussioness_backend.auth.AuthProperties;
import com.keycode.ilussioness_backend.auth.DerivedKeys;
import com.keycode.ilussioness_backend.models.Appointment;
import com.keycode.ilussioness_backend.models.AppointmentStatus;
import com.keycode.ilussioness_backend.models.CalendarConnection;
import com.keycode.ilussioness_backend.models.CalendarProvider;
import com.keycode.ilussioness_backend.repositories.AppointmentRepository;
import com.keycode.ilussioness_backend.repositories.CalendarConnectionRepository;
import com.keycode.ilussioness_backend.services.BusinessValidationException;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Connects the boutique to Google Calendar and/or Microsoft Outlook and keeps
 * appointments mirrored there. Sync is best effort: a calendar failure is
 * logged and never blocks saving an appointment.
 */
@Service
@EnableConfigurationProperties(CalendarProperties.class)
public class CalendarService {

    private static final Logger LOG = LoggerFactory.getLogger(CalendarService.class);
    private static final String STATE_ISSUER = "ilussiones-calendar-oauth";

    public record ProviderStatus(
            CalendarProvider provider, boolean configured, boolean connected,
            String accountEmail, Instant connectedAt) {
    }

    private final CalendarConnectionRepository connectionRepository;
    private final AppointmentRepository appointmentRepository;
    private final CalendarProperties properties;
    private final Map<CalendarProvider, CalendarClient> clients = new EnumMap<>(CalendarProvider.class);
    private final TokenCipher cipher;
    private final JwtEncoder stateEncoder;
    private final JwtDecoder stateDecoder;

    public CalendarService(
            CalendarConnectionRepository connectionRepository,
            AppointmentRepository appointmentRepository,
            CalendarProperties properties,
            AuthProperties authProperties,
            List<CalendarClient> calendarClients
    ) {
        this.connectionRepository = connectionRepository;
        this.appointmentRepository = appointmentRepository;
        this.properties = properties;
        calendarClients.forEach(client -> clients.put(client.provider(), client));
        String encryptionKey = properties.getTokenEncryptionKey();
        this.cipher = new TokenCipher(encryptionKey == null || encryptionKey.isBlank()
                ? authProperties.getJwtSecret() : encryptionKey);
        SecretKey stateKey = DerivedKeys.hmacKey(authProperties.getJwtSecret(), "calendar-oauth-state-v1");
        this.stateEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(stateKey));
        this.stateDecoder = NimbusJwtDecoder.withSecretKey(stateKey).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Transactional(readOnly = true)
    public List<ProviderStatus> status() {
        return clients.values().stream()
                .map(client -> {
                    Optional<CalendarConnection> connection = connectionRepository.findById(client.provider());
                    return new ProviderStatus(
                            client.provider(),
                            client.isConfigured(),
                            connection.isPresent(),
                            connection.map(CalendarConnection::getAccountEmail).orElse(null),
                            connection.map(CalendarConnection::getConnectedAt).orElse(null));
                })
                .toList();
    }

    public String authorizationUrl(CalendarProvider provider) {
        CalendarClient client = configuredClient(provider);
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(STATE_ISSUER)
                .subject(provider.name())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(600))
                .build();
        String state = stateEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return client.authorizationUrl(redirectUri(provider), state);
    }

    @Transactional
    public void completeAuthorization(CalendarProvider provider, String code, String state) {
        verifyState(provider, state);
        CalendarClient client = configuredClient(provider);
        CalendarClient.Tokens tokens = client.exchangeCode(code, redirectUri(provider));
        CalendarConnection connection = connectionRepository.findById(provider).orElseGet(CalendarConnection::new);
        connection.setProvider(provider);
        connection.setAccessTokenEncrypted(cipher.encrypt(tokens.accessToken()));
        if (tokens.refreshToken() != null) {
            connection.setRefreshTokenEncrypted(cipher.encrypt(tokens.refreshToken()));
        }
        connection.setExpiresAt(Instant.now().plusSeconds(tokens.expiresInSeconds()));
        connection.setConnectedAt(Instant.now());
        try {
            connection.setAccountEmail(client.accountEmail(tokens.accessToken()));
        } catch (RuntimeException ex) {
            LOG.warn("Could not read {} account e-mail ({})", provider, ex.getClass().getSimpleName());
        }
        connectionRepository.save(connection);
    }

    @Transactional
    public void disconnect(CalendarProvider provider) {
        connectionRepository.deleteById(provider);
    }

    /** Pushes every scheduled appointment from today on to the connected calendars. */
    @Transactional
    public int syncUpcoming() {
        if (connectionRepository.count() == 0) {
            throw new BusinessValidationException("calendar_not_connected");
        }
        List<Appointment> appointments = appointmentRepository.findByStatusAndAppointmentDateGreaterThanEqual(
                AppointmentStatus.SCHEDULED, LocalDate.now(zone()));
        appointments.forEach(this::syncAppointment);
        return appointments.size();
    }

    /** Creates, updates or (for cancelled appointments) removes the external events. */
    public void syncAppointment(Appointment appointment) {
        if (appointment == null || appointment.getAppointmentDate() == null
                || appointment.getAppointmentTime() == null) {
            return;
        }
        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            removeAppointment(appointment);
            return;
        }
        for (CalendarConnection connection : connectionRepository.findAll()) {
            CalendarClient client = clients.get(connection.getProvider());
            if (client == null || !client.isConfigured()) {
                continue;
            }
            try {
                String token = accessToken(client, connection);
                CalendarClient.Event event = toEvent(appointment);
                String eventId = eventId(appointment, connection.getProvider());
                if (eventId == null) {
                    setEventId(appointment, connection.getProvider(), client.createEvent(token, event));
                } else {
                    client.updateEvent(token, eventId, event);
                }
            } catch (RuntimeException ex) {
                LOG.warn("Calendar sync with {} failed ({})", connection.getProvider(), ex.getClass().getSimpleName());
            }
        }
    }

    public void removeAppointment(Appointment appointment) {
        if (appointment == null) {
            return;
        }
        for (CalendarConnection connection : connectionRepository.findAll()) {
            CalendarClient client = clients.get(connection.getProvider());
            String eventId = eventId(appointment, connection.getProvider());
            if (client == null || !client.isConfigured() || eventId == null) {
                continue;
            }
            try {
                client.deleteEvent(accessToken(client, connection), eventId);
                setEventId(appointment, connection.getProvider(), null);
            } catch (RuntimeException ex) {
                LOG.warn("Calendar delete in {} failed ({})", connection.getProvider(), ex.getClass().getSimpleName());
            }
        }
    }

    public static CalendarProvider parseProvider(String value) {
        try {
            return CalendarProvider.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            throw new BusinessValidationException("calendar_provider_not_supported");
        }
    }

    private String accessToken(CalendarClient client, CalendarConnection connection) {
        Instant expiresAt = connection.getExpiresAt();
        if (expiresAt != null && expiresAt.isAfter(Instant.now().plusSeconds(60))) {
            return cipher.decrypt(connection.getAccessTokenEncrypted());
        }
        String refreshToken = cipher.decrypt(connection.getRefreshTokenEncrypted());
        if (refreshToken == null) {
            return cipher.decrypt(connection.getAccessTokenEncrypted());
        }
        CalendarClient.Tokens tokens = client.refresh(refreshToken);
        connection.setAccessTokenEncrypted(cipher.encrypt(tokens.accessToken()));
        if (tokens.refreshToken() != null) {
            connection.setRefreshTokenEncrypted(cipher.encrypt(tokens.refreshToken()));
        }
        connection.setExpiresAt(Instant.now().plusSeconds(tokens.expiresInSeconds()));
        connectionRepository.save(connection);
        return tokens.accessToken();
    }

    private CalendarClient.Event toEvent(Appointment appointment) {
        LocalDateTime start = LocalDateTime.of(appointment.getAppointmentDate(), appointment.getAppointmentTime());
        String customer = appointment.getCustomer() == null ? "Cliente" : appointment.getCustomer().getName();
        String reason = appointment.getReason() == null || appointment.getReason().isBlank()
                ? "Cita" : appointment.getReason();
        String phone = appointment.getCustomer() == null || appointment.getCustomer().getPhone() == null
                ? "" : "\nTeléfono: " + appointment.getCustomer().getPhone();
        return new CalendarClient.Event(
                reason + " - " + customer,
                "Cita en Ilussiones Boutique\nCliente: " + customer + phone + "\nMotivo: " + reason,
                start,
                start.plusMinutes(properties.getEventDurationMinutes()),
                properties.getTimeZone());
    }

    private static String eventId(Appointment appointment, CalendarProvider provider) {
        return provider == CalendarProvider.GOOGLE ? appointment.getGoogleEventId() : appointment.getMicrosoftEventId();
    }

    private static void setEventId(Appointment appointment, CalendarProvider provider, String eventId) {
        if (provider == CalendarProvider.GOOGLE) {
            appointment.setGoogleEventId(eventId);
        } else {
            appointment.setMicrosoftEventId(eventId);
        }
    }

    private CalendarClient configuredClient(CalendarProvider provider) {
        CalendarClient client = clients.get(provider);
        if (client == null || !client.isConfigured()) {
            throw new BusinessValidationException("calendar_not_configured");
        }
        return client;
    }

    private void verifyState(CalendarProvider provider, String state) {
        Jwt jwt;
        try {
            jwt = stateDecoder.decode(state);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new BusinessValidationException("invalid_oauth_state");
        }
        if (!STATE_ISSUER.equals(jwt.getClaimAsString("iss")) || !provider.name().equals(jwt.getSubject())) {
            throw new BusinessValidationException("invalid_oauth_state");
        }
    }

    private String redirectUri(CalendarProvider provider) {
        String base = properties.getRedirectBaseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/api/calendar/" + provider.name().toLowerCase(Locale.ROOT) + "/callback";
    }

    ZoneId zone() {
        return ZoneId.of(properties.getTimeZone());
    }
}
