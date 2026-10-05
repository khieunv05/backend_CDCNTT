package com.example.english_app_cdcntt.service;

import com.example.english_app_cdcntt.dto.WordDto;
import java.util.List;

/**
 * §13.12 — the single write transaction for the confirm step of POST
 * /api/words/generate-topic/confirm. The AI stays outside; the caller guarantees every key
 * already has a word_cache row (step 1 of the split flow, or a fresh AI answer on a miss).
 */
public interface TopicTxService {

    /**
     * One notebook row per accepted word (reviewCount=0, due at creation), copied from its
     * shared word_cache row. Skips words that fail the stored shape, repeat inside the batch,
     * or already exist in the user's notebook; the UNIQUE (user_id, english) key stays the
     * race guard. A single skipped word never aborts the batch.
     *
     * @return the saved {@code WordDto} rows with generated ids, in the given order
     */
    List<WordDto> addWordsFromCache(Long userId, List<String> keys);
}
