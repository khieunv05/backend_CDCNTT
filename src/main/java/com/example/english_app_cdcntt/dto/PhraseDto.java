package com.example.english_app_cdcntt.dto;

import java.time.Instant;
import java.util.List;

/** §4.1 row 12 — graded paragraph with the user's title; validPhrase never reaches the HTTP layer. */
public record PhraseDto(
        Long id,
        String title,
        String text,
        String correctedText,
        int score,
        Instant createdAt,
        List<GrammarErrorDto> errors) {
}
