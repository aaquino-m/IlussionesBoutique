package com.keycode.ilussioness_backend.reminders;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on the reminder job only when {@code app.reminders.enabled=true}. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "app.reminders", name = "enabled", havingValue = "true")
public class ReminderSchedulingConfig {
}
