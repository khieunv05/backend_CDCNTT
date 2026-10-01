package com.example.english_app_cdcntt.service;

import com.example.english_app_cdcntt.enums.Level;
import com.example.english_app_cdcntt.enums.PartOfSpeech;
import java.util.List;

/**
 * §8.2 — LLM adapter contract. The input is always an already-normalized single English word; the
 * adapter owns prompt construction, HTTP handling, schema validation and error translation, so
 * callers only see {@link GeneratedMeaning} or an {@link com.example.english_app_cdcntt.exception.AiServiceException}.
 * (§8.2 also defines {@code GradingResult gradePhrase(String text)} for Phase 5 — added there.)
 */
public interface AiClient {

    /** @return a {@code validWord=false} marker (no meanings required) or a fully valid set. */
    GeneratedMeaning generateWordMeaning(String english);

    /**
     * @param validWord {@code false} when the LLM decides the input is not a dictionary word —
     *                  only this flag is required in that case
     * @param level     CEFR level, present and valid when {@code validWord}
     * @param values    1–3 complete meanings, present when {@code validWord}
     */
    record GeneratedMeaning(boolean validWord, Level level, List<MeaningItem> values) {

        public GeneratedMeaning {
            values = values == null ? List.of() : List.copyOf(values);
        }
    }

    record MeaningItem(
            String vietnamese,
            String example,
            String exampleTranslation,
            String pronunciation,
            PartOfSpeech partOfSpeech) {
    }
}
