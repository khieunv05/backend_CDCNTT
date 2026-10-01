package com.example.english_app_cdcntt.service;

import com.example.english_app_cdcntt.dto.PhraseDto;
import com.example.english_app_cdcntt.service.AiClient.GradingResult;

/** §8.1 — short write transactions for phrases; the AI call runs OUTSIDE these. */
public interface PhraseTxService {

    /** Persists the graded paragraph plus its errors atomically; maps inside the tx (lazy-safe). */
    PhraseDto save(Long userId, String text, GradingResult grading);

    /** §4.1 row 14 — deletes the owned paragraph; anything else is a 403 via the
     * {@code OwnershipDeniedException} thrown here. */
    void delete(Long phraseId, Long userId);
}
