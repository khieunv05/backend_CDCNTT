package com.example.english_app_cdcntt.service.impl;

import com.example.english_app_cdcntt.dto.GeneratedWordDto;
import com.example.english_app_cdcntt.entity.WordCache;
import com.example.english_app_cdcntt.exception.AiServiceException;
import com.example.english_app_cdcntt.exception.InvalidWordException;
import com.example.english_app_cdcntt.mapper.WordCacheMapper;
import com.example.english_app_cdcntt.service.AiClient;
import com.example.english_app_cdcntt.service.GenerateService;
import com.example.english_app_cdcntt.service.WordCacheTxService;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * §7 — generate flow: normalize → validate word/phrase → read cache (read tx) → on miss ask
 * the LLM (OUTSIDE any transaction) → write cache (one new tx) → on UNIQUE(english) race,
 * re-read in a new transaction and return the winner's row. Never fabricates a cache hit,
 * never retries the LLM, never caches failures.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GenerateServiceImpl implements GenerateService {

    /**
     * §2 — one to five dictionary words (supports phrasal verbs like "make up", "get along
     * with"); hyphens and apostrophes only inside a word unit, single spaces between units.
     */
    private static final Pattern WORD_OR_PHRASE = Pattern.compile("^[a-z]+(?:['-][a-z]+)*(?: [a-z]+(?:['-][a-z]+)*){0,4}$");

    /** Longest phrase accepted: 5 word units — see the {0,4} bound in WORD_OR_PHRASE. */
    private static final int MAX_WORDS = 5;

    private final WordCacheTxService wordCacheTxService;
    private final AiClient aiClient;
    private final WordCacheMapper wordCacheMapper;

    @Override
    public GeneratedWordDto generateWord(String rawEnglish) {
        String english = normalize(rawEnglish);
        if (english.isEmpty() || english.length() > 255 || !WORD_OR_PHRASE.matcher(english).matches()) {
            throw new InvalidWordException();
        }

        Optional<WordCache> cached = wordCacheTxService.findCached(english);
        if (cached.isPresent()) {
            return wordCacheMapper.toGenerated(cached.get());
        }

        AiClient.GeneratedMeaning meaning = aiClient.generateWordMeaning(english);
        if (!meaning.validWord()) {
            throw new InvalidWordException();
        }

        try {
            return wordCacheTxService.saveNew(english, meaning);
        } catch (DataIntegrityViolationException race) {
            // §7 — the UNIQUE(english) race: the losing write is already rolled back.
            // Only a real committed row may be returned; anything else is an infra failure.
            log.info("Cache write race for english=<redacted> — falling back to re-read");
            return wordCacheTxService.findCached(english)
                    .map(wordCacheMapper::toGenerated)
                    .orElseThrow(() -> new AiServiceException(
                            "cache race re-read found no row after UNIQUE(english) violation"));
        }
    }

    private String normalize(String raw) {
        // strip edges, collapse inner whitespace runs to single spaces, lowercase — so the
        // cache key for "make   UP " equals the key for "make up".
        return raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
