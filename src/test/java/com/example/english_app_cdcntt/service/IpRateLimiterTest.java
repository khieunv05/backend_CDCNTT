package com.example.english_app_cdcntt.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.english_app_cdcntt.config.IpRateLimitProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IpRateLimiterTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    private final AtomicReference<Instant> now = new AtomicReference<>(START);
    private final Clock clock = new Clock() {
        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    };

    private IpRateLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = new IpRateLimiter(new IpRateLimitProperties(true, 2, Duration.ofMinutes(1)), clock);
    }

    @Test
    void tryAcquire_withinWindow_allowsUpToTheLimit() {
        assertThat(limiter.tryAcquire("10.0.0.1")).isTrue();
        assertThat(limiter.tryAcquire("10.0.0.1")).isTrue();
    }

    @Test
    void tryAcquire_overLimit_blocksUntilTheWindowSlides() {
        limiter.tryAcquire("10.0.0.1");
        limiter.tryAcquire("10.0.0.1");

        assertThat(limiter.tryAcquire("10.0.0.1")).isFalse();

        now.set(START.plus(Duration.ofMinutes(1)));

        assertThat(limiter.tryAcquire("10.0.0.1")).isTrue();
    }

    @Test
    void tryAcquire_tracksEachAddressSeparately() {
        limiter.tryAcquire("10.0.0.1");
        limiter.tryAcquire("10.0.0.1");
        limiter.tryAcquire("10.0.0.1");

        assertThat(limiter.tryAcquire("10.0.0.2")).isTrue();
    }

    @Test
    void tryAcquire_whenDisabled_alwaysAllows() {
        IpRateLimiter disabled = new IpRateLimiter(
                new IpRateLimitProperties(false, 1, Duration.ofMinutes(1)), clock);

        assertThat(disabled.tryAcquire("10.0.0.1")).isTrue();
        assertThat(disabled.tryAcquire("10.0.0.1")).isTrue();
    }
}