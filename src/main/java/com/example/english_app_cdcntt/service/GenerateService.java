package com.example.english_app_cdcntt.service;

import com.example.english_app_cdcntt.dto.GeneratedWordDto;
import com.example.english_app_cdcntt.entity.WordCache;
import java.util.Optional;

/**
 * §7 — stateless coordinator of GET /api/words/generate. Deliberately holds NO transaction
 * around the AI call: the LLM may take seconds and no DB connection may be pinned to it.
 * Transactional steps are delegated to {@link WordCacheTxService}.
 */
public interface GenerateService {

    /**
     * @param rawEnglish the request parameter, verbatim from the user
     * @return the generated (or cached) word with every id {@code null}
     * @throws com.example.english_app_cdcntt.exception.InvalidWordException not a single English word
     * @throws com.example.english_app_cdcntt.exception.AiServiceException AI infrastructure failed
     */
    GeneratedWordDto generateWord(String rawEnglish);
}
