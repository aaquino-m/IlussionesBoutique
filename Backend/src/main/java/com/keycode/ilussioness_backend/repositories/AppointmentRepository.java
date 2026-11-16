package com.keycode.ilussioness_backend.repositories;

import com.keycode.ilussioness_backend.models.Appointment;
import com.keycode.ilussioness_backend.models.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    List<Appointment> findByCustomerId(UUID customerId);

    List<Appointment> findByStatusAndAppointmentDateBetweenOrderByAppointmentDateAscAppointmentTimeAsc(
            AppointmentStatus status, LocalDate from, LocalDate to);

    List<Appointment> findByStatusAndAppointmentDateGreaterThanEqual(AppointmentStatus status, LocalDate from);
}
