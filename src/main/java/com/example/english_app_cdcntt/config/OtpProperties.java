package com.example.english_app_cdcntt.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * §5.2 — the code lifetime and wrong-guess limit of the two OTP flows. Defaults match the agreed
 * policy: 10 minutes to confirm a registration, 15 to reset a password, five wrong guesses before
 * the code dies. The project is a report demo, so there is no resend cooldown, hourly send quota or
 * per-IP throttle.
 */
@ConfigurationProperties("app.otp")
@Validated
public record OtpProperties(
        @DefaultValue("10m") Duration registrationTtl,
        @DefaultValue("15m") Duration resetTtl,
        @DefaultValue("5") int maxAttempts) {

    public OtpProperties {
        requirePositive(registrationTtl, "app.otp.registration-ttl");
        requirePositive(resetTtl, "app.otp.reset-ttl");
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("app.otp.max-attempts must be at least 1");
        }
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException(name + " must be a positive duration");
        }
    }
}
