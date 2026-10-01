package com.example.english_app_cdcntt.dto;

import com.example.english_app_cdcntt.enums.Level;
import java.util.List;

/**
 * §4.1 row 7 — same shape as one {@link WordDto} element of the word list, but every id is
 * {@code null} because the answer is generated (or served from cache), not owned by the user.
 */
public record GeneratedWordDto(String english, Level level, List<WordValueDto> values) {

    public GeneratedWordDto {
        values = values == null ? List.of() : List.copyOf(values);
    }
}
