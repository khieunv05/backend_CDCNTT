package com.example.english_app_cdcntt.controller;

import com.example.english_app_cdcntt.config.UserPrincipal;
import com.example.english_app_cdcntt.dto.DueCountResponse;
import com.example.english_app_cdcntt.dto.GeneratedWordDto;
import com.example.english_app_cdcntt.dto.MessageResponse;
import com.example.english_app_cdcntt.dto.ReviewResultResponse;
import com.example.english_app_cdcntt.dto.SuccessResponse;
import com.example.english_app_cdcntt.dto.WordDto;
import com.example.english_app_cdcntt.form.ReviewForm;
import com.example.english_app_cdcntt.form.TopicForm;
import com.example.english_app_cdcntt.form.WordForm;
import com.example.english_app_cdcntt.service.GenerateService;
import com.example.english_app_cdcntt.service.TopicGenerateService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The word endpoints of §4.1 rows 5–15. Thin by design: it only moves data between the
 * contract shapes and the services, and the owner id always comes from the authenticated
 * principal (§5.3) — never from the request.
 */
@RestController
@RequestMapping("/api/words")
public class WordController {

    static final String CREATE_SUCCESS_MESSAGE = "Thêm từ thành công";
    static final String UPDATE_SUCCESS_MESSAGE = "Sửa từ thành công";
    static final String DELETE_SUCCESS_MESSAGE = "Xóa từ thành công";
    static final String TOPIC_SUCCESS_MESSAGE = "Thêm từ theo chủ đề thành công";

    private final WordService wordService;
    private final GenerateService generateService;
    private final TopicGenerateService topicGenerateService;

    public WordController(WordService wordService, GenerateService generateService,
            TopicGenerateService topicGenerateService) {
        this.wordService = wordService;
        this.generateService = generateService;
        this.topicGenerateService = topicGenerateService;
    }

    @GetMapping
    List<WordDto> list(@AuthenticationPrincipal UserPrincipal principal) {
        return wordService.listWords(principal.id());
    }

    @GetMapping("/due-count")
    DueCountResponse dueCount(@AuthenticationPrincipal UserPrincipal principal) {
        return wordService.countDue(principal.id());
    }

    /**
     * §4.1 row 11 — confirm an SRS review batch. Duplicates inside {@code wordIds} are allowed and
     * deduplicated by the service; any foreign id fails the whole batch with 403 and no partial
     * update survives (§6.3 rollback rule).
     */
    @PostMapping("/review")
    ReviewResultResponse review(@AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ReviewForm form) {
        return wordService.review(principal.id(), form);
    }

    /**
     * §4.1 row 7 — generate the meaning set of ONE English word. The shared cache makes the
     * answer independent of the caller, so the principal is only the §5.3 access gate. Body:
     * one {@link GeneratedWordDto} with null ids (same shape as a row-5 element); 400 when the
     * parameter is not a single English word, 502 when the AI service fails.
     */
    @GetMapping("/generate")
    GeneratedWordDto generate(@AuthenticationPrincipal UserPrincipal principal,
            @RequestParam String english) {
        return generateService.generateWord(english);
    }

    /**
     * §4.1 row 15 — act-20, add words by topic. The whole flow (AI topic check → AI proposal →
     * one write transaction) lives in the service; this only carries the principal as the §5.3
     * access gate. 400 when the AI rejects the topic, 502 on any AI failure — both before any
     * DB change.
     */
    @PostMapping("/generate-topic")
    @ResponseStatus(HttpStatus.CREATED)
    SuccessResponse<List<WordDto>> generateTopic(@AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody TopicForm form) {
        return new SuccessResponse<>(TOPIC_SUCCESS_MESSAGE,
                topicGenerateService.generateTopicWords(principal.id(), form.topic()));
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
