package com.example.english_app_cdcntt.dto;

/**
 * GET /api/words/due-count body (§6.1). {@code nextReview == now} counts as due, so the query
 * behind this number is {@code nextReview <= now} (§6.2).
 */
public record DueCountResponse(long dueCount) {
}
