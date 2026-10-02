package com.example.english_app_cdcntt.service;

import com.example.english_app_cdcntt.repository.RefreshTokenRepository;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * §9.1 — daily cleanup of expired refresh tokens. The schedule comes from
 * application.yml (cron 03:00, zone UTC); the cutoff is taken from the injected
 * {@link Clock} so tests drive it with a fixed time (§12).
 */
@Component
public class TokenCleanupService {

    private static final Logger log = LoggerFactory.getLogger(TokenCleanupService.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final Clock clock;

    public TokenCleanupService(RefreshTokenRepository refreshTokenRepository, Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.clock = clock;
    }

    /** Deletes every token whose expiry is at or before now; returns the removed count. */
    @Scheduled(cron = "${app.cleanup.cron}", zone = "${app.cleanup.zone}")
    @Transactional
    public int cleanupExpiredTokens() {
        Instant cutoff = clock.instant();
        int deleted = refreshTokenRepository.deleteAllExpiredBefore(cutoff);
        if (deleted > 0) {
            log.info("TokenCleanupService removed {} expired refresh tokens (cutoff={})", deleted, cutoff);
        }
        return deleted;
    }
}
