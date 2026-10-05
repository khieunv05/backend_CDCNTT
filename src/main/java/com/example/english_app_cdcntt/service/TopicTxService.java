package com.example.english_app_cdcntt.service;

import com.example.english_app_cdcntt.dto.WordDto;
import com.example.english_app_cdcntt.service.AiClient.TopicWord;
import java.util.List;

/**
 * §13.12 — the single write transaction for a whole generate-topic batch. The AI stays
 * outside; this interface receives the already-proposed words and persists them atomically.
 */
public interface TopicTxService {

    /**
     * Cache-first per word, then one notebook row per accepted word (reviewCount=0, due at
     * creation). Skips words that fail the stored shape, repeat inside the batch, or already
     * exist in the user's notebook; the UNIQUE (user_id, english) key stays the race guard.
     *
     * @return the saved {@code WordDto} rows with generated ids, in the given order
     */
    List<WordDto> saveBatch(Long userId, List<TopicWord> words);
}
