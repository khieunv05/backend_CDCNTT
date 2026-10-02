package com.example.english_app_cdcntt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.english_app_cdcntt.repository.RefreshTokenRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** §9.1/§12 — daily token cleanup: fixed-clock cutoff and removed-count passthrough. */
@ExtendWith(MockitoExtension.class)
class TokenCleanupServiceTest {

    private static final Instant NOW = ZonedDateTime.of(2026, 1, 1, 3, 0, 0, 0, ZoneId.of("UTC")).toInstant();

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private TokenCleanupService tokenCleanupService;

    @BeforeEach
    void setUp() {
        tokenCleanupService = new TokenCleanupService(refreshTokenRepository, Clock.fixed(NOW, ZoneId.of("UTC")));
    }

    @Test
    @DisplayName("cutoff = giờ hiện tại (fix clock); trả về số dòng bị xoá")
    void cleanupUsesCurrentTimeAsCutoff() {
        when(refreshTokenRepository.deleteAllExpiredBefore(NOW)).thenReturn(5);

        int deleted = tokenCleanupService.cleanupExpiredTokens();

        assertThat(deleted).isEqualTo(5);
        verify(refreshTokenRepository).deleteAllExpiredBefore(NOW);
    }

    @Test
    @DisplayName("không có token hết hạn → 0, không lỗi")
    void cleanupWithNothingExpiredReturnsZero() {
        when(refreshTokenRepository.deleteAllExpiredBefore(NOW)).thenReturn(0);

        assertThat(tokenCleanupService.cleanupExpiredTokens()).isZero();
        verify(refreshTokenRepository).deleteAllExpiredBefore(NOW);
    }
}
