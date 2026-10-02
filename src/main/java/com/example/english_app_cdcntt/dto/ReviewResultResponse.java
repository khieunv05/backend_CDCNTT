package com.example.english_app_cdcntt.dto;

/**
 * §4.1 row 11 — body of POST /api/words/review: how many distinct words were confirmed. It is the
 * distinct count even when the client sent duplicates (§6.3:292). Not idempotent (§6.3:297).
 */
public record ReviewResultResponse(int reviewedCount) {
}
