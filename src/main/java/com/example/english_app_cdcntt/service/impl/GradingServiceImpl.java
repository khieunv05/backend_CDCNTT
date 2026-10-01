package com.example.english_app_cdcntt.service.impl;

import com.example.english_app_cdcntt.exception.AiServiceException;
import com.example.english_app_cdcntt.exception.InvalidPhraseException;
import com.example.english_app_cdcntt.service.AiClient;
import com.example.english_app_cdcntt.service.AiClient.GradingResult;
import com.example.english_app_cdcntt.service.GradingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * §7 — grading flow: defensive length re-check → AI. The adapter maps the provider
 * "not a paragraph" flag to {@link InvalidPhraseException} (§8.2:355) and every schema
 * problem to {@link AiServiceException}; this service only re-checks the input envelope.
 */
@Service
@RequiredArgsConstructor
public class GradingServiceImpl implements GradingService {

    static final int MIN_TEXT = 10;
    static final int MAX_TEXT = 5000;

    private final AiClient aiClient;

    @Override
    public GradingResult grade(String text) {
        if (text == null || text.strip().length() < MIN_TEXT
                || text.length() > MAX_TEXT) {
            throw new InvalidPhraseException();
        }
        return aiClient.gradePhrase(text);
    }
}
