package com.example.english_app_cdcntt.service.impl;

import com.example.english_app_cdcntt.dto.PhraseDto;
import com.example.english_app_cdcntt.entity.GrammarError;
import com.example.english_app_cdcntt.entity.Phrase;
import com.example.english_app_cdcntt.exception.OwnershipDeniedException;
import com.example.english_app_cdcntt.mapper.PhraseMapper;
import com.example.english_app_cdcntt.repository.PhraseRepository;
import com.example.english_app_cdcntt.repository.UserRepository;
import com.example.english_app_cdcntt.service.AiClient.GradingResult;
import com.example.english_app_cdcntt.service.PhraseTxService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** §8.1 — one tx per write: save (phrase + errors) and owned-delete. */
@Service
@RequiredArgsConstructor
public class PhraseTxServiceImpl implements PhraseTxService {

    private final PhraseRepository phraseRepository;
    private final UserRepository userRepository;
    private final PhraseMapper phraseMapper;

    @Override
    @Transactional
    public PhraseDto save(Long userId, String text, GradingResult grading) {
        // §8.2:355 — the adapter guarantees a valid result here: the provider
        // "not a paragraph" flag became InvalidPhraseException upstream.
        Phrase phrase = Phrase.create(userRepository.getReferenceById(userId), text,
                grading.correctedText(), grading.score());
        grading.errors().forEach(error -> phrase.addError(GrammarError.create(error.incorrect(),
                error.correction(), error.explanation())));
        return phraseMapper.toDto(phraseRepository.saveAndFlush(phrase));
    }

    @Override
    @Transactional
    public void delete(Long phraseId, Long userId) {
        Phrase phrase = phraseRepository.findOwnedForUpdate(phraseId, userId)
                .orElseThrow(OwnershipDeniedException::deletePhrase);
        phraseRepository.delete(phrase);
        phraseRepository.flush();
    }
}
