package com.example.english_app_cdcntt.service.impl;

import com.example.english_app_cdcntt.dto.PhraseDto;
import com.example.english_app_cdcntt.form.PhraseForm;
import com.example.english_app_cdcntt.mapper.PhraseMapper;
import com.example.english_app_cdcntt.repository.PhraseRepository;
import com.example.english_app_cdcntt.service.PhraseService;
import com.example.english_app_cdcntt.service.PhraseTxService;
import com.example.english_app_cdcntt.service.GradingService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * §8.1 — thin orchestration: grading (AI) runs outside any transaction; the write tx is inside
 * {@link PhraseTxService}. Mapping happens inside the read tx so lazy error rows load.
 */
@Service
@RequiredArgsConstructor
public class PhraseServiceImpl implements PhraseService {

    private final PhraseRepository phraseRepository;
    private final PhraseTxService phraseTxService;
    private final GradingService gradingService;
    private final PhraseMapper phraseMapper;

    @Override
    @Transactional(readOnly = true)
    public List<PhraseDto> list(Long userId) {
        return phraseRepository.findByUser_IdOrderByIdAsc(userId).stream()
                .map(phraseMapper::toDto)
                .toList();
    }

    @Override
    public PhraseDto create(Long userId, PhraseForm form) {
        return phraseTxService.save(userId, form.text(), gradingService.grade(form.text()));
    }

    @Override
    public void delete(Long userId, Long phraseId) {
        phraseTxService.delete(phraseId, userId);
    }
}
