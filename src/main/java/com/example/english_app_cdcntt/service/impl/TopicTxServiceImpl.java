package com.example.english_app_cdcntt.service.impl;

import com.example.english_app_cdcntt.dto.WordDto;
import com.example.english_app_cdcntt.entity.Word;
import com.example.english_app_cdcntt.entity.WordValue;
import com.example.english_app_cdcntt.form.WordForm;
import com.example.english_app_cdcntt.mapper.WordMapper;
import com.example.english_app_cdcntt.repository.UserRepository;
import com.example.english_app_cdcntt.repository.WordRepository;
import com.example.english_app_cdcntt.service.TopicTxService;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * §13.12 — one write transaction for the whole confirmed generate-topic batch (step 2 of the
 * split flow). Per the user decision of 2026-10-05, rows are built straight from the
 * {@link WordForm} payloads the caller validated (no word_cache lookup, no AI call —
 * word_cache stays step-1-only shared data). reviewCount=0 and due at creation, act-20
 * step 5. A single skipped word never aborts the batch.
 */
@Service
@RequiredArgsConstructor
public class TopicTxServiceImpl implements TopicTxService {

    /** Same stored shape as GenerateServiceImpl — defense in depth behind the form checks. */
    private static final Pattern WORD_OR_PHRASE =
            Pattern.compile("^[a-z]+(?:['-][a-z]+)*(?: [a-z]+(?:['-][a-z]+)*){0,4}$");

    private final WordRepository wordRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    @Override
    @Transactional
    public List<WordDto> addWords(Long userId, List<WordForm> forms) {
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        Set<String> batchKeys = new HashSet<>();
        List<WordDto> saved = new ArrayList<>();
        for (WordForm form : forms) {
            String english = normalize(form.english());
            if (english.isEmpty() || english.length() > 255
                    || !WORD_OR_PHRASE.matcher(english).matches()) {
                continue; // wrong stored shape — skip the word, not the batch
            }
            if (!batchKeys.add(english)) {
                continue; // repeated inside the confirmed list
            }
            if (wordRepository.existsByUser_IdAndEnglish(userId, english)) {
                continue; // already in this notebook — skipped, batch continues
            }
            Word word = Word.create(userRepository.getReferenceById(userId), english, now);
            word.updateLevel(form.level());
            form.values().forEach(v -> word.addValue(WordValue.create(
                    v.vietnamese(), v.example(), v.exampleTranslation(),
                    v.pronunciation(), v.partOfSpeech())));
            saved.add(WordMapper.toDto(wordRepository.saveAndFlush(word)));
        }
        return List.copyOf(saved);
    }

    private static String normalize(String english) {
        return english.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
