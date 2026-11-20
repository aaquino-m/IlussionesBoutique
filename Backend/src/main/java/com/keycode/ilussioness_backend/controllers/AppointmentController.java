package com.keycode.ilussioness_backend.controllers;

import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestParam;

import com.keycode.ilussioness_backend.models.Appointment;
import com.keycode.ilussioness_backend.services.AppointmentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController // Recibir peticiones, JSON
@RequestMapping("/api/appointment") 
@CrossOrigin(origins = "*") 
public class AppointmentController {

    private final AppointmentService appointmentService;

    @Value("${app.calendar.time-zone:America/El_Salvador}")
    private String timeZone;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @GetMapping
    public List<Appointment> getAllAppointments() {
        return appointmentService.getAllAppointments();
    }

    @PostMapping
    public Appointment createAppointment(@Valid @RequestBody Appointment appointment) {
        return appointmentService.createAppointment(appointment);
    }

    /** Scheduled appointments starting within the next {@code hours} (1-336). */
    @GetMapping("/upcoming")
    public List<Appointment> getUpcomingAppointments(@RequestParam(defaultValue = "48") int hours) {
        int window = Math.max(1, Math.min(hours, 336));
        return appointmentService.getUpcomingAppointments(window, ZoneId.of(timeZone));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Appointment> getAppointmentById(@PathVariable UUID id) {
        return appointmentService.getAppointmentById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Appointment> updateAppointment(
            @PathVariable UUID id,
            @RequestBody Appointment appointment
    ) {
        return appointmentService.updateAppointment(id, appointment)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAppointment(@PathVariable UUID id) {
        if (!appointmentService.deleteAppointment(id)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<Appointment>> getAppointmentsByCustomer(@PathVariable UUID customerId) {
        List<Appointment> appointments = appointmentService.getAppointmentsByCustomer(customerId);
        return ResponseEntity.ok(appointments);
    }
}
