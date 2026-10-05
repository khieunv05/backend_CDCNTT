package com.example.english_app_cdcntt.controller;

import com.example.english_app_cdcntt.config.UserPrincipal;
import com.example.english_app_cdcntt.dto.MessageResponse;
import com.example.english_app_cdcntt.dto.PhraseDto;
import com.example.english_app_cdcntt.dto.SuccessResponse;
import com.example.english_app_cdcntt.form.PhraseForm;
import com.example.english_app_cdcntt.service.PhraseService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * §4.1 rows 12–14 — the phrase notebook, all data owned by the authenticated principal (§5.3).
 * Thin by design: grading orchestration lives in {@link PhraseService}, the write transaction in
 * {@code PhraseTxService}, mapping in {@code PhraseMapper}.
 */
@RestController
@RequestMapping("/api/phrases")
public class PhraseController {

    static final String CREATE_SUCCESS_MESSAGE = "Thêm đoạn văn thành công";
    static final String DELETE_SUCCESS_MESSAGE = "Xóa đoạn văn thành công";

    private final PhraseService phraseService;

    public PhraseController(PhraseService phraseService) {
        this.phraseService = phraseService;
    }

    /** §4.1 row 12 — the principal's graded paragraphs, ordered by {@code id} ascending. */
    @GetMapping
    List<PhraseDto> list(@AuthenticationPrincipal UserPrincipal principal) {
        return phraseService.list(principal.id());
    }

    /**
     * §4.1 row 13 — grade the paragraph with the AI, store text + corrected text + score + error
     * rows atomically; 400 for a §9.2 phrase violation (Bean Validation or backend rule), 502
     * when the AI service fails (§8.2:359 — every AI failure is infrastructure).
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    SuccessResponse<PhraseDto> create(@AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody PhraseForm form) {
        return new SuccessResponse<>(CREATE_SUCCESS_MESSAGE, phraseService.create(principal.id(), form));
    }

    /** §4.1 row 14 — owned delete; 403 when the row is missing or belongs to someone else. */
    @DeleteMapping("/{id}")
    MessageResponse delete(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        phraseService.delete(principal.id(), id);
        return new MessageResponse(DELETE_SUCCESS_MESSAGE);
    }
}
