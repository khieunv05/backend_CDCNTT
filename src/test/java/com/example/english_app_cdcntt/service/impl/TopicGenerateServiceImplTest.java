package com.example.english_app_cdcntt.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.english_app_cdcntt.dto.WordDto;
import com.example.english_app_cdcntt.entity.User;
import com.example.english_app_cdcntt.entity.Word;
import com.example.english_app_cdcntt.enums.Level;
import com.example.english_app_cdcntt.enums.PartOfSpeech;
import com.example.english_app_cdcntt.exception.AiServiceException;
import com.example.english_app_cdcntt.exception.InvalidTopicException;
import com.example.english_app_cdcntt.repository.WordRepository;
import com.example.english_app_cdcntt.service.AiClient;
import com.example.english_app_cdcntt.service.TopicTxService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Plain unit tests for the §7.1 generate-topic coordinator: topic gate (400), AI failure (502),
 * exclusion list from the notebook, and the hand-off to the one write transaction.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TopicGenerateServiceImplTest {

    @Mock
    private AiClient aiClient;

    @Mock
    private WordRepository wordRepository;

    @Mock
    private TopicTxService topicTxService;

    private TopicGenerateServiceImpl topicGenerateService;

    @BeforeEach
    void setUp() {
        topicGenerateService = new TopicGenerateServiceImpl(aiClient, wordRepository, topicTxService);
    }

    private AiClient.TopicWord topicWord(String english) {
        return new AiClient.TopicWord(english, Level.B1, List.of(new AiClient.MeaningItem(
                "nghĩa", "example", "dịch", "/ipa/", PartOfSpeech.NOUN)));
    }

    private Word ownedWord(String english) {
        // Word's constructor requires a non-null user and nextReview (Objects.requireNonNull) —
        // the exclude list only reads Word::getEnglish, so throwaway values are fine here
        return Word.create(User.create("alice", "hash"), english, Instant.parse("2026-01-15T08:00:00Z"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t\n"})
    @DisplayName("chủ đề rỗng sau chuẩn hóa → 400, không đụng AI/DB (contract @NotBlank dư trên controller)")
    void blankTopicIsRejectedBeforeAnyCall(String raw) {
        assertThatThrownBy(() -> topicGenerateService.generateTopicWords(1L, raw))
                .isInstanceOf(InvalidTopicException.class);
        verifyNoInteractions(aiClient, wordRepository, topicTxService);
    }

    @Test
    @DisplayName("AI từ chối chủ đề (validTopic=false) → 400, không lấy exclude, không lưu")
    void aiRejectedTopicIs400() {
        when(aiClient.checkTopic("fjaskdf")).thenReturn(false);

        assertThatThrownBy(() -> topicGenerateService.generateTopicWords(1L, "fjaskdf"))
                .isInstanceOf(InvalidTopicException.class)
                .hasMessage(InvalidTopicException.MESSAGE);
        verify(aiClient, never()).generateTopicWords(anyString(), anyList());
        verifyNoInteractions(wordRepository, topicTxService);
    }

    @Test
    @DisplayName("AI lỗi (timeout/schema sai) → AiServiceException 502, không lưu gì")
    void aiFailurePropagates() {
        when(aiClient.checkTopic("Travel")).thenReturn(true);
        when(aiClient.generateTopicWords(anyString(), anyList()))
                .thenThrow(new AiServiceException("429"));

        assertThatThrownBy(() -> topicGenerateService.generateTopicWords(1L, "Travel"))
                .isInstanceOf(AiServiceException.class);
        verify(topicTxService, never()).saveBatch(1L, List.of());
    }

    @Test
    @DisplayName("happy path: gửi chủ đề đã chuẩn hóa + danh sách từ đã có làm exclude, giao cả lô cho 1 tx")
    void happyPathPassesExcludeListAndDelegatesToTx() {
        // the service normalizes first, so the AI sees the collapsed topic, not the raw input
        when(aiClient.checkTopic("Du lịch")).thenReturn(true);
        when(wordRepository.findByUser_IdOrderByIdAsc(1L))
                .thenReturn(List.of(ownedWord("travel"), ownedWord("hotel")));
        List<AiClient.TopicWord> proposed = List.of(topicWord("airport"), topicWord("luggage"));
        when(aiClient.generateTopicWords("Du lịch", List.of("travel", "hotel")))
                .thenReturn(proposed);
        when(topicTxService.saveBatch(1L, proposed)).thenReturn(List.of());

        topicGenerateService.generateTopicWords(1L, "  Du   lịch  ");

        verify(aiClient).generateTopicWords("Du lịch", List.of("travel", "hotel"));
        verify(topicTxService).saveBatch(1L, proposed);
    }

    @Test
    @DisplayName("sổ từ trống → exclude rỗng vẫn gọi AI sinh từ (không chặn)")
    void emptyNotebookStillProposes() {
        when(aiClient.checkTopic("Food")).thenReturn(true);
        when(wordRepository.findByUser_IdOrderByIdAsc(1L)).thenReturn(List.of());
        List<AiClient.TopicWord> proposed = List.of(topicWord("restaurant"));
        when(aiClient.generateTopicWords("Food", List.of())).thenReturn(proposed);
        when(topicTxService.saveBatch(1L, proposed)).thenReturn(List.of());

        topicGenerateService.generateTopicWords(1L, "Food");

        verify(aiClient).generateTopicWords("Food", List.of());
        verify(topicTxService).saveBatch(1L, proposed);
    }

    @Test
    @DisplayName("trả về đúng danh sách WordDto của tx service (không biến đổi)")
    void returnsTxResultAsIs() {
        when(aiClient.checkTopic("Food")).thenReturn(true);
        when(wordRepository.findByUser_IdOrderByIdAsc(1L)).thenReturn(List.of());
        List<AiClient.TopicWord> proposed = List.of(topicWord("restaurant"));
        when(aiClient.generateTopicWords("Food", List.of())).thenReturn(proposed);
        List<WordDto> result = List.of();
        when(topicTxService.saveBatch(1L, proposed)).thenReturn(result);

        List<WordDto> actual = topicGenerateService.generateTopicWords(1L, "Food");

        assertThat(actual).isSameAs(result);
    }
}
