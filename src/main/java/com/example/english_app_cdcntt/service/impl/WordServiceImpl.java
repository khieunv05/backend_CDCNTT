package com.example.english_app_cdcntt.service.impl;

import com.example.english_app_cdcntt.dto.DueCountResponse;
import com.example.english_app_cdcntt.dto.WordDto;
import com.example.english_app_cdcntt.entity.Word;
import com.example.english_app_cdcntt.entity.WordValue;
import com.example.english_app_cdcntt.exception.DuplicateWordException;
import com.example.english_app_cdcntt.exception.InvalidRequestException;
import com.example.english_app_cdcntt.exception.OwnershipDeniedException;
import com.example.english_app_cdcntt.form.WordForm;
import com.example.english_app_cdcntt.form.WordValueForm;
import com.example.english_app_cdcntt.mapper.WordMapper;
import com.example.english_app_cdcntt.repository.UserRepository;
import com.example.english_app_cdcntt.repository.WordRepository;
import com.example.english_app_cdcntt.service.WordService;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Words CRUD and due-count (§6.2). Reads run in a read-only transaction so DTO mapping can touch
 * the LAZY meanings while the session is still open (§1.4); writes take the row lock with
 * {@code PESSIMISTIC_WRITE} and rewrite the meanings in the same transaction. Every §6.2 check runs
 * before the first mutation, so a rejected request leaves the row untouched.
 */
@Service
@Transactional(readOnly = true)
public class WordServiceImpl implements WordService {

    private final WordRepository wordRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public WordServiceImpl(WordRepository wordRepository, UserRepository userRepository, Clock clock) {
        this.wordRepository = wordRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Override
    public List<WordDto> listWords(Long userId) {
        return wordRepository.findByUser_IdOrderByIdAsc(userId).stream()
                .map(WordMapper::toDto)
                .toList();
    }

    @Override
    public DueCountResponse countDue(Long userId) {
        // LessThanEqual: a word whose nextReview equals now is already due (§6.2:279).
        return new DueCountResponse(wordRepository.countByUser_IdAndNextReviewLessThanEqual(userId, now()));
    }

    @Override
    @Transactional
    public WordDto createWord(Long userId, WordForm form) {
        form.values().forEach(value -> {
            if (value.id() != null) {
                throw InvalidRequestException.meaningIdOnCreate();
            }
        });
        String english = normalize(form.english());
        if (wordRepository.existsByUser_IdAndEnglish(userId, english)) {
            throw new DuplicateWordException();
        }
        Word word = Word.create(userRepository.getReferenceById(userId), english, now());
        word.updateLevel(form.level());
        form.values().forEach(value -> word.addValue(toEntity(value)));
        // Flush so the response carries generated ids; the UNIQUE key stays the race guard (§6.2:287).
        return WordMapper.toDto(wordRepository.saveAndFlush(word));
    }

    @Override
    @Transactional
    public WordDto updateWord(Long userId, Long wordId, WordForm form) {
        // §6.2:281 — lock and ownership first: missing/foreign answers 403 before the duplicate check.
        Word word = wordRepository.findOwnedForUpdate(wordId, userId)
                .orElseThrow(OwnershipDeniedException::updateWord);
        String english = normalize(form.english());
        if (!english.equals(word.getEnglish()) && wordRepository.existsByUser_IdAndEnglish(userId, english)) {
            throw new DuplicateWordException();
        }
        applyValues(word, form.values());
        word.rename(english);
        word.updateLevel(form.level());
        return WordMapper.toDto(wordRepository.saveAndFlush(word));
    }

    @Override
    @Transactional
    public void deleteWord(Long userId, Long wordId) {
        Word word = wordRepository.findOwnedForUpdate(wordId, userId)
                .orElseThrow(OwnershipDeniedException::deleteWord);
        wordRepository.delete(word); // meanings follow: orphanRemoval plus ON DELETE CASCADE
    }

    /**
     * §6.2:283–285 — validates the whole form before mutating anything: an id must be one of this
     * word's meanings and must not repeat in the form; a null id creates a meaning and an
     * unreferenced row is removed through orphanRemoval.
     */
    private static void applyValues(Word word, List<WordValueForm> forms) {
        Map<Long, WordValue> existing = new HashMap<>();
        word.getWordValues().forEach(value -> existing.put(value.getId(), value));
        Set<Long> seen = new HashSet<>();
        Map<Long, WordValue> kept = new LinkedHashMap<>();
        for (WordValueForm form : forms) {
            if (form.id() == null) {
                continue;
            }
            // Unknown, foreign and repeated ids share one 400 message so nothing about ownership leaks.
            if (!seen.add(form.id()) || !existing.containsKey(form.id())) {
                throw InvalidRequestException.unknownMeaningId();
            }
            kept.put(form.id(), existing.get(form.id()));
        }
        forms.forEach(form -> {
            if (form.id() == null) {
                word.addValue(toEntity(form));
            } else {
                kept.get(form.id()).updateDetails(form.vietnamese(), form.example(),
                        form.exampleTranslation(), form.pronunciation(), form.partOfSpeech());
            }
        });
        existing.forEach((id, value) -> {
            if (!kept.containsKey(id)) {
                word.removeValue(value);
            }
        });
    }

    private static WordValue toEntity(WordValueForm form) {
        return WordValue.create(form.vietnamese(), form.example(), form.exampleTranslation(),
                form.pronunciation(), form.partOfSpeech());
    }

    /** §2.1:58 — one normalized key for lookup, duplicate check and storage. */
    private static String normalize(String english) {
        return english.strip().toLowerCase(Locale.ROOT);
    }

    /** §2.1:56 — one {@code now} per operation, truncated to the DATETIME(6) precision. */
    private Instant now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }
}