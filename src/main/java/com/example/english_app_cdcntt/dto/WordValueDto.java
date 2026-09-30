package com.example.english_app_cdcntt.dto;

import com.example.english_app_cdcntt.enums.PartOfSpeech;

/** One meaning of a word in an API response (§6.1); only {@code vietnamese} is mandatory. */
public record WordValueDto(
        Long id,
        String vietnamese,
        String example,
        String exampleTranslation,
        String pronunciation,
        PartOfSpeech partOfSpeech) {
}
