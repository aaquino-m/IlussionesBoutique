package com.keycode.ilussioness_backend.repositories;

import com.keycode.ilussioness_backend.models.Appointment;
import com.keycode.ilussioness_backend.models.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    List<Appointment> findByCustomerId(UUID customerId);

    List<Appointment> findByStatusAndAppointmentDateBetweenOrderByAppointmentDateAscAppointmentTimeAsc(
            AppointmentStatus status, LocalDate from, LocalDate to);

    List<Appointment> findByStatusAndAppointmentDateGreaterThanEqual(AppointmentStatus status, LocalDate from);

    /** Sets only {@code reminder_sent_at}, so concurrent edits to the appointment are not overwritten. */
    @Transactional
    @Modifying
    @Query("update Appointment a set a.reminderSentAt = :sentAt where a.id in :ids and a.reminderSentAt is null")
    int markReminderSent(@Param("ids") List<UUID> ids, @Param("sentAt") OffsetDateTime sentAt);
}
