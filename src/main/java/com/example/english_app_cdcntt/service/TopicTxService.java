package com.example.english_app_cdcntt.service;

import com.example.english_app_cdcntt.dto.WordDto;
import com.example.english_app_cdcntt.form.WordForm;
import java.util.List;

/**
 * §13.12 — the single write transaction for the confirm step of POST
 * /api/words/generate-topic/confirm. Per the user decision of 2026-10-05 the notebook rows
 * are built straight from the {@link WordForm} payloads the user reviewed on screen — no
 * word_cache lookup and no AI call here; the AI stays outside transactions entirely.
 */
public interface TopicTxService {

    /**
     * One notebook row per accepted form (reviewCount=0, due at creation), with level and
     * values taken from the form (id on nested values is ignored, createWord semantics).
     * Skips words that fail the stored shape, repeat inside the batch, or already exist in
     * the user's notebook; the UNIQUE (user_id, english) key stays the race guard. A single
     * skipped word never aborts the batch.
     *
     * @return the saved {@code WordDto} rows with generated ids, in the given order
     */
    List<WordDto> addWords(Long userId, List<WordForm> forms);
}
