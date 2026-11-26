package com.example.english_app_cdcntt.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * In-memory IP throttle for the two endpoints an attacker can hammer without an account. It is a
 * second line of defense behind the per-email counters in the database: the database quota is
 * authoritative for "5 codes per hour per email", this one only slows down one host spraying many
 * mailboxes. Keep it in memory on purpose — a shared store would make the API depend on Redis for
 * a control that only protects mail quota.
 */
@ConfigurationProperties("app.ip-rate-limit")
@Validated
public record IpRateLimitProperties(@DefaultValue("true") boolean enabled,
        @DefaultValue("30") int maxRequests, @DefaultValue("PT1H") Duration window) {

    public IpRateLimitProperties {
        if (maxRequests < 1) {
            throw new IllegalArgumentException("app.ip-rate-limit.max-requests must be at least 1");
        }
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("app.ip-rate-limit.window must be a positive duration");
        }
    }
}
