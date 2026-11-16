package com.keycode.ilussioness_backend.repositories;

import com.keycode.ilussioness_backend.models.CalendarConnection;
import com.keycode.ilussioness_backend.models.CalendarProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CalendarConnectionRepository extends JpaRepository<CalendarConnection, CalendarProvider> {
}
