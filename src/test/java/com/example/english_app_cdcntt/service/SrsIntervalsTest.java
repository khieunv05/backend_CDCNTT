package com.example.english_app_cdcntt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Bảng SRS điều chỉnh 2026-10-02 (yêu cầu user): 1→3→7→14→30. */
class SrsIntervalsTest {

    private static final Instant NOW = Instant.parse("2026-10-02T00:00:00Z");

    @Test
    void ladderMatchesUserTable() {
        assertThat(SrsIntervals.nextReview(1, NOW)).isEqualTo(NOW.plus(Duration.ofDays(1)));
        assertThat(SrsIntervals.nextReview(2, NOW)).isEqualTo(NOW.plus(Duration.ofDays(3)));
        assertThat(SrsIntervals.nextReview(3, NOW)).isEqualTo(NOW.plus(Duration.ofDays(7)));
        assertThat(SrsIntervals.nextReview(4, NOW)).isEqualTo(NOW.plus(Duration.ofDays(14)));
        assertThat(SrsIntervals.nextReview(5, NOW)).isEqualTo(NOW.plus(Duration.ofDays(30)));
    }

    @Test
    void capsAtLastInterval() {
        assertThat(SrsIntervals.nextReview(6, NOW)).isEqualTo(NOW.plus(Duration.ofDays(30)));
        assertThat(SrsIntervals.nextReview(50, NOW)).isEqualTo(NOW.plus(Duration.ofDays(30)));
    }

    @Test
    void rejectsZeroOrNegative() {
        assertThatThrownBy(() -> SrsIntervals.nextReview(0, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
