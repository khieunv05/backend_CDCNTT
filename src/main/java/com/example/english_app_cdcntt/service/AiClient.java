package com.example.english_app_cdcntt.service;

import com.example.english_app_cdcntt.enums.Level;
import com.example.english_app_cdcntt.enums.PartOfSpeech;
import java.util.List;

/**
 * §8.2 — LLM adapter contract. Inputs are always pre-validated by the service layer: the generate
 * call takes an already-normalized word/phrase, the grading call takes a length-checked text. The
 * adapter owns prompt construction, HTTP handling, strict schema validation and error translation,
 * so callers only see the result records or an
 * {@link com.example.english_app_cdcntt.exception.AiServiceException}. Business outcomes that are
 * valid at the schema level ({@code validWord=false}, {@code validPhrase=false}) pass through
 * untouched for the service layer to map to 4xx.
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

    /**
     * §7.1 step 2 — act-20 topic gate. {@code false} is a valid schema answer ("chủ đề không
     * hợp lệ") the service maps to 400; anything wrong with the call itself is AiServiceException.
     */
    boolean checkTopic(String topic);

    /**
     * §7.1 step 4 — propose 10 topic words avoiding the given already-owned list. The topic and
     * the exclusion list are data, never instructions. Schema failure is AiServiceException.
     */
    List<TopicWord> generateTopicWords(String topic, List<String> excludeEnglish);

    /**
     * One proposed word. {@code level} is required here (unlike {@link GeneratedMeaning}) — the
     * notebook copy and the possible word_cache row are built from this proposal in one tx.
     */
    record TopicWord(String english, Level level, List<MeaningItem> values) {

        public TopicWord {
            values = values == null ? List.of() : List.copyOf(values);
        }
    }

    /** §8.2 — grade a prepared English paragraph (10–5000 chars, already length-checked). */
    /**
     * Grades a pre-validated paragraph. Provider "not a paragraph" classification
     * (§8.2:355) surfaces as {@code InvalidPhraseException}; transport/schema
     * failures as {@code AiServiceException}.
     */
    GradingResult gradePhrase(String text);

    /**
     * §8.2:348 — successful grading contract. {@code false} from the provider
     * schema flag never reaches this record: the adapter maps it to
     * {@code InvalidPhraseException} (§8.2:355), so callers only ever see a
     * fully valid result (0–10 score, non-blank correctedText, ≤100 errors).
     */
    record GradingResult(
            int score,
            String correctedText,
            List<GradingError> errors) {

        public GradingResult {
            errors = errors == null ? List.of() : List.copyOf(errors);
        }
    }

    /**
     * @param incorrect   span of the original text, may be empty when the issue is style only
     * @param correction  suggested replacement, may be empty when the issue is deletion
     * @param explanation non-blank reason in Vietnamese
     */
    record GradingError(String incorrect, String correction, String explanation) {
    }
}
