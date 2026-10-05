package com.example.english_app_cdcntt.service.impl;

import com.example.english_app_cdcntt.dto.WordDto;
import com.example.english_app_cdcntt.enums.Level;
import com.example.english_app_cdcntt.entity.Word;
import com.example.english_app_cdcntt.entity.WordCache;
import com.example.english_app_cdcntt.entity.WordValue;
import com.example.english_app_cdcntt.mapper.WordMapper;
import com.example.english_app_cdcntt.repository.UserRepository;
import com.example.english_app_cdcntt.repository.WordRepository;
import com.example.english_app_cdcntt.service.AiClient;
import com.example.english_app_cdcntt.service.AiClient.TopicWord;
import com.example.english_app_cdcntt.service.TopicTxService;
import com.example.english_app_cdcntt.service.WordCacheTxService;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * §13.12 — one write transaction for the whole accepted generate-topic batch. Per word:
 * cache-first reuse of word_cache, otherwise create the cache rows from the AI proposal,
 * then a notebook row owned by the user (reviewCount=0, due at creation, act-20 step 5).
 */
@Service
@RequiredArgsConstructor
public class TopicTxServiceImpl implements TopicTxService {

    /** Same stored shape as GenerateServiceImpl — the AI is a data source, not a trusted one. */
    private static final Pattern WORD_OR_PHRASE =
            Pattern.compile("^[a-z]+(?:['-][a-z]+)*(?: [a-z]+(?:['-][a-z]+)*){0,4}$");

    private final WordRepository wordRepository;
    private final WordCacheTxService wordCacheTxService;
    private final UserRepository userRepository;
    private final Clock clock;

    @Override
    @Transactional
    public List<WordDto> saveBatch(Long userId, List<TopicWord> words) {
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        Set<String> batchKeys = new HashSet<>();
        List<WordDto> saved = new ArrayList<>();
        for (TopicWord topicWord : words) {
            String english = normalize(topicWord.english());
            if (english.length() > 255 || !WORD_OR_PHRASE.matcher(english).matches()) {
                continue; // wrong stored shape — skip the word, not the batch
            }
            if (!batchKeys.add(english)) {
                continue; // repeated inside the AI answer
            }
            if (wordRepository.existsByUser_IdAndEnglish(userId, english)) {
                continue; // already in this notebook — skipped, batch continues
            }
            Level level;
            List<AiClient.MeaningItem> values;
            Optional<WordCache> cached = wordCacheTxService.findCached(english);
            if (cached.isPresent()) {
                level = cached.get().getLevel();
                values = cached.get().getWordCacheValues().stream()
                        .map(v -> new AiClient.MeaningItem(v.getVietnamese(), v.getExample(),
                                v.getExampleTranslation(), v.getPronunciation(), v.getPartOfSpeech()))
                        .toList();
            } else {
                level = topicWord.level();
                values = topicWord.values();
                wordCacheTxService.saveNew(english,
                        new AiClient.GeneratedMeaning(true, level, values));
            }
            Word word = Word.create(userRepository.getReferenceById(userId), english, now);
            word.updateLevel(level);
            values.forEach(v -> word.addValue(WordValue.create(v.vietnamese(), v.example(),
                    v.exampleTranslation(), v.pronunciation(), v.partOfSpeech())));
            saved.add(WordMapper.toDto(wordRepository.saveAndFlush(word)));
        }
        return List.copyOf(saved);
    }

    private static String normalize(String english) {
        return english.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
