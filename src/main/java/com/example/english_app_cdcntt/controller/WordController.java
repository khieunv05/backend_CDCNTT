package com.example.english_app_cdcntt.controller;

import com.example.english_app_cdcntt.config.UserPrincipal;
import com.example.english_app_cdcntt.dto.DueCountResponse;
import com.example.english_app_cdcntt.dto.MessageResponse;
import com.example.english_app_cdcntt.dto.SuccessResponse;
import com.example.english_app_cdcntt.dto.WordDto;
import com.example.english_app_cdcntt.form.WordForm;
import com.example.english_app_cdcntt.service.WordService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The five word endpoints of Phase 3 (§4.1 rows 5, 6, 8, 9, 10). Thin by design: it only moves data
 * between the contract shapes and {@link WordService}, and the owner id always comes from the
 * authenticated principal (§5.3) — never from the request. {@code GET /api/words/generate} (row 7)
 * arrives in Phase 4 and {@code POST /api/words/review} (row 11) in Phase 6.
 */
@RestController
@RequestMapping("/api/words")
public class WordController {

    static final String CREATE_SUCCESS_MESSAGE = "Thêm từ thành công";
    static final String UPDATE_SUCCESS_MESSAGE = "Sửa từ thành công";
    static final String DELETE_SUCCESS_MESSAGE = "Xóa từ thành công";

    private final WordService wordService;

    public WordController(WordService wordService) {
        this.wordService = wordService;
    }

    @GetMapping
    List<WordDto> list(@AuthenticationPrincipal UserPrincipal principal) {
        return wordService.listWords(principal.id());
    }

    @GetMapping("/due-count")
    DueCountResponse dueCount(@AuthenticationPrincipal UserPrincipal principal) {
        return wordService.countDue(principal.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    SuccessResponse<WordDto> create(@AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody WordForm form) {
        return new SuccessResponse<>(CREATE_SUCCESS_MESSAGE, wordService.createWord(principal.id(), form));
    }

    @PutMapping("/{id}")
    SuccessResponse<WordDto> update(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody WordForm form) {
        return new SuccessResponse<>(UPDATE_SUCCESS_MESSAGE, wordService.updateWord(principal.id(), id, form));
    }

    @DeleteMapping("/{id}")
    MessageResponse delete(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        wordService.deleteWord(principal.id(), id);
        return new MessageResponse(DELETE_SUCCESS_MESSAGE);
    }
}
