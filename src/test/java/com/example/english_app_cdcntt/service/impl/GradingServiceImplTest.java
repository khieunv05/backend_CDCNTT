package com.example.english_app_cdcntt.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.english_app_cdcntt.exception.InvalidPhraseException;
import com.example.english_app_cdcntt.service.AiClient;
import com.example.english_app_cdcntt.service.AiClient.GradingResult;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * §12 — Grading biên: 10/5000 ký tự, chuỗi rỗng, AI non-phrase, ủy quyền AI.
 */
@ExtendWith(MockitoExtension.class)
class GradingServiceImplTest {

    private static final String MIN_TEXT = "abcdefghij";      // 10 ký tự — biên dưới
    private static final String BELOW_MIN = "abcdefghi";     // 9 ký tự
    private static final String MAX_TEXT = "a".repeat(5000); // biên trên
    private static final String OVER_MAX = "a".repeat(5001);

    @Mock
    private AiClient aiClient;

    @InjectMocks
    private GradingServiceImpl gradingService;

    private static GradingResult grading(int score) {
        return new GradingResult(score, "fixed text", List.of());
    }

    @Test
    @DisplayName("9 ký tự → 400 InvalidPhrase, không gọi AI")
    void rejectsBelowMinWithoutCallingAi() {
        assertThatThrownBy(() -> gradingService.grade(BELOW_MIN))
                .isInstanceOf(InvalidPhraseException.class);
        verify(aiClient, never()).gradePhrase(anyString());
    }

    @Test
    @DisplayName("5001 ký tự → 400 InvalidPhrase, không gọi AI")
    void rejectsOverMaxWithoutCallingAi() {
        assertThatThrownBy(() -> gradingService.grade(OVER_MAX))
                .isInstanceOf(InvalidPhraseException.class);
        verify(aiClient, never()).gradePhrase(anyString());
    }

    @Test
    @DisplayName("chuỗi rỗng/khoảng trắng → 400 InvalidPhrase")
    void rejectsBlankWithoutCallingAi() {
        assertThatThrownBy(() -> gradingService.grade("   "))
                .isInstanceOf(InvalidPhraseException.class);
        verify(aiClient, never()).gradePhrase(anyString());
    }

    @Test
    @DisplayName("biên 10 ký tự hợp lệ → ủy quyền AI, trả nguyên kết quả")
    void delegatesMinBoundary() {
        GradingResult result = grading(9);
        when(aiClient.gradePhrase(MIN_TEXT)).thenReturn(result);

        assertThat(gradingService.grade(MIN_TEXT)).isSameAs(result);

        verify(aiClient).gradePhrase(MIN_TEXT);
    }

    @Test
    @DisplayName("đoạn 5000 ký tự hợp lệ → gọi AI với nguyên văn")
    void delegatesMaxBoundary() {
        when(aiClient.gradePhrase(MAX_TEXT)).thenReturn(grading(10));

        assertThat(gradingService.grade(MAX_TEXT).score()).isEqualTo(10);

        verify(aiClient).gradePhrase(MAX_TEXT);
    }
}
