package com.example.english_app_cdcntt.service;

import com.example.english_app_cdcntt.repository.PasswordResetCodeRepository;
import com.example.english_app_cdcntt.repository.PendingRegistrationRepository;
import com.example.english_app_cdcntt.repository.RefreshTokenRepository;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * §9.1 — daily cleanup of everything that expires: refresh tokens, pending registrations and
 * password-reset codes. The schedule comes from application.yml (cron 03:00, zone UTC); the
 * cutoff is taken from the injected {@link Clock} so tests drive it with a fixed time (§12).
 *
 * <p>The {@code app.cleanup.enabled} flag (default {@code true}) gates the whole
 * bean, so a {@code false} value in any profile (e.g. the test profile) also
 * removes the {@code @Scheduled} registration — {@code @ConditionalOnProperty}
 * is evaluated at bean registration time, not per tick.</p>
 */
@Component
@ConditionalOnProperty(name = "app.cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class TokenCleanupService {

    private static final Logger log = LoggerFactory.getLogger(TokenCleanupService.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final PendingRegistrationRepository pendingRegistrationRepository;
    private final PasswordResetCodeRepository passwordResetCodeRepository;
    private final Clock clock;

    public TokenCleanupService(RefreshTokenRepository refreshTokenRepository,
            PendingRegistrationRepository pendingRegistrationRepository,
            PasswordResetCodeRepository passwordResetCodeRepository, Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.pendingRegistrationRepository = pendingRegistrationRepository;
        this.passwordResetCodeRepository = passwordResetCodeRepository;
        this.clock = clock;
    }

    /** Deletes every expired row of the three tables; returns the total removed count. */
    @Scheduled(cron = "${app.cleanup.cron}", zone = "${app.cleanup.zone}")
    @Transactional
    public int cleanupExpiredTokens() {
        Instant cutoff = clock.instant();
        int refreshTokens = refreshTokenRepository.deleteAllExpiredBefore(cutoff);
        int drafts = pendingRegistrationRepository.deleteAllExpiredBefore(cutoff);
        int resetCodes = passwordResetCodeRepository.deleteAllExpiredBefore(cutoff);
        int deleted = refreshTokens + drafts + resetCodes;
        if (deleted > 0) {
            log.info("TokenCleanupService removed {} expired refresh tokens, {} registration drafts, "
                    + "{} reset codes (cutoff={})", refreshTokens, drafts, resetCodes, cutoff);
        }
        return deleted;
    }
}
