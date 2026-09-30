package com.example.english_app_cdcntt.service;

import com.example.english_app_cdcntt.dto.DueCountResponse;
import com.example.english_app_cdcntt.dto.WordDto;
import com.example.english_app_cdcntt.exception.DuplicateWordException;
import com.example.english_app_cdcntt.exception.OwnershipDeniedException;
import com.example.english_app_cdcntt.form.WordForm;
import java.util.List;

/**
 * Contract of the five word endpoints of Phase 3 (§4.1 rows 5, 6, 8, 9, 10; §6.2). Every method is
 * scoped by the owner id and the controller takes that id from the authenticated principal, so a
 * caller can never reach another user's notebook. Impl: {@code service.impl.WordServiceImpl}.
 */
public interface WordService {

    /** §6.2 — the user's words ordered by id with their meanings; no pagination (array contract). */
    List<WordDto> listWords(Long userId);

    /** §6.2 — {@code nextReview <= now}; a word due exactly at {@code now} counts. */
    DueCountResponse countDue(Long userId);

    /**
     * §6.2 — creates with {@code reviewCount = 0} and {@code nextReview = now}; the word and all of
     * its meanings are written in one transaction.
     *
     * @throws DuplicateWordException when the normalized english is already in this notebook
     */
    WordDto createWord(Long userId, WordForm form);

    /**
     * §6.2 — rewrites an owned word: meanings with an id must belong to this word, absent ids are
     * created, unreferenced rows are removed; {@code reviewCount} and {@code nextReview} survive.
     *
     * @throws OwnershipDeniedException when the word is missing or owned by somebody else (403)
     * @throws DuplicateWordException   when the new normalized english collides with another word
     */
    WordDto updateWord(Long userId, Long wordId, WordForm form);

    /**
     * §6.2 — deletes the word and its meanings in one transaction.
     *
     * @throws OwnershipDeniedException when the word is missing or owned by somebody else (403)
     */
    void deleteWord(Long userId, Long wordId);
}
