package com.example.english_app_cdcntt.mapper;

import com.example.english_app_cdcntt.dto.WordDto;
import com.example.english_app_cdcntt.dto.WordValueDto;
import com.example.english_app_cdcntt.entity.Word;
import com.example.english_app_cdcntt.entity.WordValue;
import java.util.Comparator;

/**
 * Entity → DTO translation for words (§3: {@code mapper/ WordMapper}). Static and stateless like
 * the other mappers; it reads managed entities, so it must run inside the transaction that loaded
 * them (§1.4 — LAZY meanings and open-in-view off).
 */
public final class WordMapper {

    private WordMapper() {
    }

    /**
     * @param word a persisted word with its meanings loaded; ids are assigned and the meanings are
     *             ordered by id as §6.2 requires
     */
    public static WordDto toDto(Word word) {
        return new WordDto(
                word.getId(),
                word.getEnglish(),
                word.getLevel(),
                word.getReviewCount(),
                word.getNextReview(),
                word.getCreatedAt(),
                word.getUpdatedAt(),
                word.getWordValues().stream()
                        .sorted(Comparator.comparing(WordValue::getId))
                        .map(WordMapper::toValueDto)
                        .toList());
    }

    public static WordValueDto toValueDto(WordValue value) {
        return new WordValueDto(
                value.getId(),
                value.getVietnamese(),
                value.getExample(),
                value.getExampleTranslation(),
                value.getPronunciation(),
                value.getPartOfSpeech());
    }
}
