package com.example.english_app_cdcntt.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * §5.2 — the code lifetime and abuse limits of the two OTP flows. Defaults match the agreed
 * policy: 10 minutes to confirm a registration, 15 to reset a password, one resend per minute,
 * five codes per hour per email, five wrong guesses before the code dies.
 */
@ConfigurationProperties("app.otp")
@Validated
public record OtpProperties(
        @DefaultValue("10m") Duration registrationTtl,
        @DefaultValue("15m") Duration resetTtl,
        @DefaultValue("60s") Duration resendCooldown,
        @DefaultValue("1h") Duration sendWindow,
        @DefaultValue("5") int maxSendsPerWindow,
        @DefaultValue("5") int maxAttempts) {

    public OtpProperties {
        requirePositive(registrationTtl, "app.otp.registration-ttl");
        requirePositive(resetTtl, "app.otp.reset-ttl");
        requirePositive(resendCooldown, "app.otp.resend-cooldown");
        requirePositive(sendWindow, "app.otp.send-window");
        if (maxSendsPerWindow < 1) {
            throw new IllegalArgumentException("app.otp.max-sends-per-window must be at least 1");
        }
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
