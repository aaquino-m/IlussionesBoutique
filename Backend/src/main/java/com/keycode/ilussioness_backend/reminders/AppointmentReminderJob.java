package com.keycode.ilussioness_backend.reminders;

import com.keycode.ilussioness_backend.models.Appointment;
import com.keycode.ilussioness_backend.models.AppointmentStatus;
import com.keycode.ilussioness_backend.notifications.EmailService;
import com.keycode.ilussioness_backend.repositories.AppointmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * E-mails the boutique a digest of appointments starting within
 * {@code app.reminders.lead-hours}. Each appointment is reminded once
 * ({@code reminder_sent_at}); rescheduling it clears the mark.
 */
@Component
public class AppointmentReminderJob {

    private static final Logger LOG = LoggerFactory.getLogger(AppointmentReminderJob.class);
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final AppointmentRepository appointmentRepository;
    private final EmailService emailService;
    private final String notifyEmail;
    private final int leadHours;
    private final ZoneId zone;

    public AppointmentReminderJob(
            AppointmentRepository appointmentRepository,
            EmailService emailService,
            @Value("${app.reminders.notify-email:}") String notifyEmail,
            @Value("${app.reminders.lead-hours:24}") int leadHours,
            @Value("${app.calendar.time-zone:America/El_Salvador}") String timeZone
    ) {
        this.appointmentRepository = appointmentRepository;
        this.emailService = emailService;
        this.notifyEmail = notifyEmail;
        this.leadHours = leadHours;
        this.zone = ZoneId.of(timeZone);
    }

    @Scheduled(cron = "${app.reminders.cron:0 */15 * * * *}", zone = "${app.calendar.time-zone:America/El_Salvador}")
    public void run() {
        try {
            sendDueReminders();
        } catch (RuntimeException ex) {
            LOG.warn("Appointment reminder job failed ({})", ex.getClass().getSimpleName());
        }
    }

    /** @return number of appointments included in the reminder. */
    @Transactional
    public int sendDueReminders() {
        LocalDateTime now = LocalDateTime.now(zone);
        LocalDateTime limit = now.plusHours(leadHours);
        List<Appointment> due = appointmentRepository
                .findByStatusAndAppointmentDateBetweenOrderByAppointmentDateAscAppointmentTimeAsc(
                        AppointmentStatus.SCHEDULED, now.toLocalDate(), limit.toLocalDate())
                .stream()
                .filter(a -> a.getReminderSentAt() == null)
                .filter(a -> {
                    LocalDateTime start = LocalDateTime.of(a.getAppointmentDate(), a.getAppointmentTime());
                    return !start.isBefore(now) && !start.isAfter(limit);
                })
                .toList();
        if (due.isEmpty()) {
            return 0;
        }
        if (notifyEmail == null || notifyEmail.isBlank()) {
            LOG.warn("{} upcoming appointment(s) but app.reminders.notify-email is not set", due.size());
            return 0;
        }
        String lines = due.stream()
                .map(a -> "- " + LocalDateTime.of(a.getAppointmentDate(), a.getAppointmentTime()).format(FORMAT)
                        + " | " + (a.getCustomer() == null ? "Cliente" : a.getCustomer().getName())
                        + (a.getCustomer() == null || a.getCustomer().getPhone() == null
                                ? "" : " (" + a.getCustomer().getPhone() + ")")
                        + " | " + (a.getReason() == null ? "Cita" : a.getReason()))
                .collect(Collectors.joining("\n"));
        String body = "Citas en las próximas " + leadHours + " horas:\n\n" + lines + "\n\n— Ilussiones Boutique";
        if (emailService.send(notifyEmail, "Recordatorio: " + due.size() + " cita(s) próximas", body)) {
            OffsetDateTime sentAt = OffsetDateTime.now(zone);
            due.forEach(a -> a.setReminderSentAt(sentAt));
            appointmentRepository.saveAll(due);
        }
        return due.size();
    }
}
