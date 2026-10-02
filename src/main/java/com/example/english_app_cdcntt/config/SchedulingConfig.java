package com.example.english_app_cdcntt.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * §9.1 — enables the {@code @Scheduled} cleanup of expired refresh tokens. Test contexts
 * inherit this but the 03:00 UTC cron never fires inside a test run; unit tests drive
 * {@code TokenCleanupService} directly with a fixed clock (§12).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
