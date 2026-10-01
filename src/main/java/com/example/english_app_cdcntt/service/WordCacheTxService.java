package com.example.english_app_cdcntt.service;

import com.example.english_app_cdcntt.dto.GeneratedWordDto;
import com.example.english_app_cdcntt.entity.WordCache;
import java.util.Optional;

/**
 * §7 — the transactional steps of the generate flow, isolated in one helper so the
 * coordinator ({@code GenerateServiceImpl}) itself runs WITHOUT a class-level transaction:
 * no connection is held while the LLM answers. The write method opens one NEW transaction
 * and flushes inside it, so the UNIQUE(english) race surfaces here while the transaction
 * is already rolled back — exactly the §7 contract for the catch-race-and-reread strategy.
 */
public interface WordCacheTxService {

    /** Read transaction with the values eagerly fetched (LAZY association). */
    Optional<WordCache> findCached(String english);

    /**
     * Writes the cache entry and all of its meanings in ONE new transaction and returns the
     * response DTO. Flushes before returning so a UNIQUE(english) violation is raised inside
     * this transaction (rolled back before the caller sees the exception).
     */
    GeneratedWordDto saveNew(String english, AiClient.GeneratedMeaning meaning);
}
