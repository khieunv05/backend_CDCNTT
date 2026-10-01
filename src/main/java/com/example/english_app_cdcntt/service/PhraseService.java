package com.example.english_app_cdcntt.service;

import com.example.english_app_cdcntt.dto.PhraseDto;
import com.example.english_app_cdcntt.form.PhraseForm;
import java.util.List;

/** §4.1 rows 12–14 — the phrase notebook: list, grade-and-save, owned-delete. */
public interface PhraseService {

    List<PhraseDto> list(Long userId);

    PhraseDto create(Long userId, PhraseForm form);

    void delete(Long userId, Long phraseId);
}
