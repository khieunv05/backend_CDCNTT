package com.example.english_app_cdcntt.dto;

import com.example.english_app_cdcntt.enums.Level;
import java.time.Instant;
import java.util.List;

/**
 * A word of the current user's notebook (§4:186 example). {@code english} is the normalized key
 * (§2.1:58) and {@code values} is ordered by id (§6.2).
 */
public record WordDto(
        Long id,
        String english,
        Level level,
        Integer reviewCount,
        Instant nextReview,
        Instant createdAt,
        Instant updatedAt,
        List<WordValueDto> values) {
}
