package com.example.english_app_cdcntt.service;

import com.example.english_app_cdcntt.dto.WordDto;
import java.util.List;

/**
 * §7.1 — coordinator for POST /api/words/generate-topic (act-20). Runs OUTSIDE any
 * transaction: AI calls are the long part (§13.12), the single write transaction for the
 * whole accepted batch belongs to {@link TopicTxService#saveBatch}.
 */
public interface TopicGenerateService {

    /**
     * Full flow for one topic: normalize → AI topic check → collect the notebook's current
     * words as the exclusion list → AI proposes 10 words → persist the accepted batch.
     *
     * @return the {@code WordDto} of each word ADDED by this call, in AI order; already-owned
     *         or repeated words are skipped, not a batch abort (§13.12)
     * @throws com.example.english_app_cdcntt.exception.InvalidTopicException
     *         when the AI rejects the topic (contract 400, no DB change)
     * @throws com.example.english_app_cdcntt.exception.AiServiceException
     *         on any transport/schema failure (contract 502, no DB change)
     */
    List<WordDto> generateTopicWords(Long userId, String rawTopic);
}
