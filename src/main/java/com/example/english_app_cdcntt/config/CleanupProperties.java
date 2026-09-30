package com.example.english_app_cdcntt.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.validation.annotation.Validated;

import java.time.DateTimeException;
import java.time.ZoneId;

@ConfigurationProperties("app.cleanup")
@Validated
public record CleanupProperties(
        @DefaultValue("true") boolean enabled,
        @NotBlank @DefaultValue("0 0 3 * * *") String cron,
        @NotBlank @DefaultValue("UTC") String zone) {

    @AssertTrue(message = "Cleanup cron must be a valid Spring cron expression")
    public boolean isCronValid() {
        return cron != null && CronExpression.isValidExpression(cron);
    }

    @AssertTrue(message = "Cleanup zone must be a valid time zone ID")
    public boolean isZoneValid() {
        if (zone == null) {
            return false;
        }
        try {
            ZoneId.of(zone);
            return true;
        } catch (DateTimeException ignored) {
            return false;
        }
    }
}
