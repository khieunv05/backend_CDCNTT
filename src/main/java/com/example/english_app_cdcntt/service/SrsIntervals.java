package com.example.english_app_cdcntt.service;

import java.time.Duration;
import java.time.Instant;

/**
 * §6.3:296–298 — fixed SRS spacing table in whole 24h days, keyed by {@code reviewCount} AFTER the
 * increment: 1→1d, 2→3d, 3→7d, 4→14d, ≥5→30d. Bảng điều chỉnh theo yêu cầu user (2026-10-02),
 * thay bảng gốc 1/2/4/7/15/30 của spec. Fixed table, never configurable.
 */
public final class SrsIntervals {

    private static final Duration[] INTERVALS = {
            Duration.ofDays(1), Duration.ofDays(3), Duration.ofDays(7),
            Duration.ofDays(14), Duration.ofDays(30),
    };

    private SrsIntervals() {
    }

    /** Next due instant for a word whose reviewCount is already {@code reviewCount} (≥1). */
    public static Instant nextReview(int reviewCount, Instant now) {
        if (reviewCount < 1) {
            throw new IllegalArgumentException("reviewCount must be at least 1");
        }
        return now.plus(INTERVALS[Math.min(reviewCount, INTERVALS.length) - 1]);
    }
}
