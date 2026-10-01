package com.example.english_app_cdcntt.mapper;

import com.example.english_app_cdcntt.dto.GeneratedWordDto;
import com.example.english_app_cdcntt.dto.WordValueDto;
import com.example.english_app_cdcntt.entity.WordCache;
import com.example.english_app_cdcntt.entity.WordCacheValue;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * §8.2 — maps a shared word-cache row into the response of GET /api/words/generate.
 * Every id is {@code null}: the cache is invisible to users. Values are ordered by id ASC
 * (same rule as §6.2 for the user word list) and must be read inside a transaction because
 * the association is LAZY.
 */
@Component
public class WordCacheMapper {

    public GeneratedWordDto toGenerated(WordCache cache) {
        List<WordValueDto> values = cache.getWordCacheValues().stream()
                .sorted(Comparator.comparing(WordCacheValue::getId))
                .map(WordCacheMapper::toValueDto)
                .toList();
        return new GeneratedWordDto(cache.getEnglish(), cache.getLevel(), values);
    }

    private static WordValueDto toValueDto(WordCacheValue value) {
        return new WordValueDto(
                null,
                value.getVietnamese(),
                value.getExample(),
                value.getExampleTranslation(),
                value.getPronunciation(),
                value.getPartOfSpeech());
    }
}
