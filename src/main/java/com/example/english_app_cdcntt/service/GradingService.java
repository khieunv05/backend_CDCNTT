package com.example.english_app_cdcntt.service;

import com.example.english_app_cdcntt.service.AiClient.GradingResult;

/** §8.2 — grades a prepared paragraph (10–5000 chars). Implementation translates every provider
 * failure into {@code AiServiceException}; a {@code validPhrase=false} answer becomes an
 * {@code InvalidPhraseException} here so controllers never see provider specifics. */
public interface GradingService {

    GradingResult grade(String text);
}
