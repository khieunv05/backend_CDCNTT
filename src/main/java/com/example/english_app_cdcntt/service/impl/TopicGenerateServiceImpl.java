package com.example.english_app_cdcntt.service.impl;

import com.example.english_app_cdcntt.dto.GeneratedWordDto;
import com.example.english_app_cdcntt.dto.WordDto;
import com.example.english_app_cdcntt.entity.Word;
import com.example.english_app_cdcntt.entity.WordCache;
import com.example.english_app_cdcntt.exception.AiServiceException;
import com.example.english_app_cdcntt.exception.InvalidTopicException;
import com.example.english_app_cdcntt.exception.InvalidWordException;
import com.example.english_app_cdcntt.form.WordForm;
import com.example.english_app_cdcntt.mapper.WordCacheMapper;
import com.example.english_app_cdcntt.repository.WordRepository;
import com.example.english_app_cdcntt.service.AiClient;
import com.example.english_app_cdcntt.service.TopicGenerateService;
import com.example.english_app_cdcntt.service.TopicTxService;
import com.example.english_app_cdcntt.service.WordCacheTxService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * §7.1 (act-20 split flow) — the non-transactional half of the two topic endpoints.
 * Step 1 (generate-topic): normalize → AI topic check (else 400 before any DB access) →
 * feed the notebook's current words to the AI as the exclusion list → AI proposes 10 words →
 * cache-first each accepted word; the notebook is NOT touched. Step 2 (confirm): the user
 * sends back full {@link WordForm} payloads and confirm trusts them (user decision
 * 2026-10-05) — no cache lookup, no AI call, only shape validation and dedupe, then the one
 * write transaction adds the notebook rows.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TopicGenerateServiceImpl implements TopicGenerateService {

    /** Same stored shape as GenerateServiceImpl — the AI is a data source, not a trusted one. */
    private static final Pattern WORD_OR_PHRASE =
            Pattern.compile("^[a-z]+(?:['-][a-z]+)*(?: [a-z]+(?:['-][a-z]+)*){0,4}$");

    private final AiClient aiClient;
    private final WordRepository wordRepository;
    private final WordCacheTxService wordCacheTxService;
    private final WordCacheMapper wordCacheMapper;
    private final TopicTxService topicTxService;

    @Override
    public List<GeneratedWordDto> generateTopicWords(Long userId, String rawTopic) {
        String topic = normalizeTopic(rawTopic);
        if (topic.isBlank()) {
            throw new InvalidTopicException();
        }
        if (!aiClient.checkTopic(topic)) {
            throw new InvalidTopicException();
        }
        List<String> exclude = wordRepository.findByUser_IdOrderByIdAsc(userId).stream()
                .map(Word::getEnglish)
                .toList();
        Set<String> owned = exclude.stream()
                .map(TopicGenerateServiceImpl::normalizeEnglish)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));

        List<GeneratedWordDto> proposed = new ArrayList<>();
        Set<String> batchKeys = new HashSet<>();
        for (AiClient.TopicWord suggestion : aiClient.generateTopicWords(topic, exclude)) {
            String english = normalizeEnglish(suggestion.english());
            if (english.isEmpty() || english.length() > 255
                    || !WORD_OR_PHRASE.matcher(english).matches()) {
                continue; // wrong stored shape — skip the word, not the batch
            }
            if (!batchKeys.add(english) || owned.contains(english)) {
                continue; // repeated inside the answer or already in this notebook
            }
            proposed.add(cacheFirst(english, suggestion));
        }
        return List.copyOf(proposed);
    }

    /**
     * §7 — cache-first for one proposed word: reuse the shared row, else persist this AI
     * proposal; on the UNIQUE(english) race re-read and return the winner's row (the losing
     * write is already rolled back — same contract as {@link GenerateServiceImpl}).
     */
    private GeneratedWordDto cacheFirst(String english, AiClient.TopicWord suggestion) {
        Optional<WordCache> cached = wordCacheTxService.findCached(english);
        if (cached.isPresent()) {
            return wordCacheMapper.toGenerated(cached.get());
        }
        try {
            return wordCacheTxService.saveNew(english,
                    new AiClient.GeneratedMeaning(true, suggestion.level(), suggestion.values()));
        } catch (DataIntegrityViolationException race) {
            log.info("Cache write race for english=<redacted> — falling back to re-read");
            return wordCacheTxService.findCached(english)
                    .map(wordCacheMapper::toGenerated)
                    .orElseThrow(() -> new AiServiceException(
                            "cache race re-read found no row after UNIQUE(english) violation"));
        }
    }

    @Override
    public List<WordDto> confirmTopicWords(Long userId, List<WordForm> forms) {
        // Phase A — validate + dedupe the WordForm payloads the user reviewed on screen.
        // Per the user decision of 2026-10-05 the confirm step trusts this payload: it does
        // NOT consult word_cache (word_cache stays step-1-only shared data) and never calls
        // the AI, so this phase is plain CPU work and the AI stays outside transactions.
        Set<String> keys = new LinkedHashSet<>();
        List<WordForm> unique = new ArrayList<>();
        for (WordForm form : forms) {
            String english = normalizeEnglish(form.english());
            if (english.isEmpty() || english.length() > 255
                    || !WORD_OR_PHRASE.matcher(english).matches()) {
                throw new InvalidWordException();
            }
            if (keys.add(english)) {
                unique.add(form);
            }
        }
        // Phase B — the single write transaction that adds the notebook rows.
        return topicTxService.addWords(userId, List.copyOf(unique));
    }

    /** Collapse whitespace runs, keep the caller's casing (a topic may be Vietnamese). */
    private static String normalizeTopic(String rawTopic) {
        return rawTopic == null ? "" : rawTopic.strip().replaceAll("\\s+", " ");
    }

    /** §2 stored shape — strip edges, lowercase, collapse inner whitespace runs. */
    private static String normalizeEnglish(String raw) {
        return raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
