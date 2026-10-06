package com.example.english_app_cdcntt.service;

import com.example.english_app_cdcntt.dto.GeneratedWordDto;
import com.example.english_app_cdcntt.dto.WordDto;
import com.example.english_app_cdcntt.form.WordForm;
import java.util.List;

/**
 * §7.1 (act-20, split into two user actions) — coordinator for the topic flow, and it runs
 * OUTSIDE any transaction: AI calls are the long part (§13.12). Step 1, POST
 * /api/words/generate-topic, only proposes and caches; step 2, POST
 * /api/words/generate-topic/confirm, is the single write transaction owned by
 * {@link TopicTxService#addWords}.
 */
public interface TopicGenerateService {

    /**
     * Step 1 — propose words for a topic WITHOUT touching the user's notebook: normalize the
     * topic, have the AI confirm it (else 400 before any DB access), collect the notebook's
     * current words as the exclusion list, ask the AI for 10 new words, then cache-first every
     * accepted word (MISS persists word_cache/word_cache_values from the AI proposal; HIT
     * reuses the shared row). No Word/WordValue row is created here.
     *
     * @return the proposed words in AI order with null ids (shared-cache shape); words with a
     *         wrong stored shape, repeated inside the answer, or already in the notebook are
     *         skipped, not a batch abort (§13.12)
     * @throws com.example.english_app_cdcntt.exception.InvalidTopicException
     *         when the AI rejects the topic (contract 400, no DB change)
     * @throws com.example.english_app_cdcntt.exception.AiServiceException
     *         on any transport/schema failure (contract 502, no DB change)
     */
    List<GeneratedWordDto> generateTopicWords(Long userId, String rawTopic);

    /**
     * Step 2 — add the words the user picked on screen into the notebook. Per the user
     * decision of 2026-10-05 the body carries full {@link WordForm} payloads and confirm
     * trusts them: Phase A (no tx) normalizes each english, validates the stored shape
     * (else 400) and dedupes in the given order — NO cache lookup, NO AI call. Phase B: one
     * write transaction via {@link TopicTxService#addWords}.
     *
     * @return the {@code WordDto} of each word ADDED by this call, in the given order;
     *         already-owned or repeated words are skipped, not a batch abort (§13.12)
     * @throws com.example.english_app_cdcntt.exception.InvalidWordException
     *         when a picked value is not a valid single English word/phrase (contract 400)
     */
    List<WordDto> confirmTopicWords(Long userId, List<WordForm> forms);
}
