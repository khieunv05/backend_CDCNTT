package com.example.english_app_cdcntt.service;

import com.example.english_app_cdcntt.config.IpRateLimitProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/**
 * Fixed-window counter per client address. Deliberately small: one map, one lock per address, no
 * scheduler. {@code X-Forwarded-For} is ignored because the API is reached directly in this
 * deployment; behind a reverse proxy, configure Spring's {@code ForwardedHeaderFilter} instead of
 * trusting a header here.
 */
@Component
public class IpRateLimiter {

    private static final int PURGE_THRESHOLD = 10_000;

    private final IpRateLimitProperties properties;
    private final Clock clock;
    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();

    public IpRateLimiter(IpRateLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /** Counts one call for {@code clientIp}; false when the fixed window is already full. */
    public boolean tryAcquire(String clientIp) {
        if (!properties.enabled()) {
            return true;
        }
        Instant now = clock.instant();
        Window window = windows.compute(clientIp,
                (key, current) -> current == null || !now.isBefore(current.startedAt.plus(properties.window()))
                        ? new Window(now)
                        : current);
        boolean allowed;
        synchronized (window) {
            allowed = window.used < properties.maxRequests();
            if (allowed) {
                window.used++;
            }
        }
        purgeIfCrowded(now);
        return allowed;
    }

    private void purgeIfCrowded(Instant now) {
        if (windows.size() > PURGE_THRESHOLD) {
            windows.entrySet()
                    .removeIf(entry -> !now.isBefore(entry.getValue().startedAt.plus(properties.window())));
        }
    }

    private static final class Window {

        private final Instant startedAt;
        private int used;

        private Window(Instant startedAt) {
            this.startedAt = startedAt;
        }
    }
}