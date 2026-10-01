package com.example.english_app_cdcntt.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.english_app_cdcntt.dto.GrammarErrorDto;
import com.example.english_app_cdcntt.dto.PhraseDto;
import com.example.english_app_cdcntt.entity.Phrase;
import com.example.english_app_cdcntt.entity.User;
import com.example.english_app_cdcntt.exception.AiServiceException;
import com.example.english_app_cdcntt.exception.InvalidPhraseException;
import com.example.english_app_cdcntt.exception.OwnershipDeniedException;
import com.example.english_app_cdcntt.form.PhraseForm;
import com.example.english_app_cdcntt.mapper.PhraseMapper;
import com.example.english_app_cdcntt.repository.PhraseRepository;
import com.example.english_app_cdcntt.service.AiClient.GradingResult;
import com.example.english_app_cdcntt.service.GradingService;
import com.example.english_app_cdcntt.service.PhraseTxService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** §7 — phrase orchestration: grade via AI, persist via tx, read via repo; no partial writes. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PhraseServiceImplTest {

    private static final long USER_ID = 7L;

    @Mock private PhraseRepository phraseRepository;
    @Mock private PhraseTxService phraseTxService;
    @Mock private GradingService gradingService;
    @Mock private PhraseMapper phraseMapper;

    private PhraseServiceImpl phraseService;

    private final GradingResult goodGrading =
            new GradingResult(8, "I went to the store yesterday.",
                    List.of(new AiClientGradingErrorHolder().error()));

    /** Small holder to keep imports tidy. */
    private static final class AiClientGradingErrorHolder {
        com.example.english_app_cdcntt.service.AiClient.GradingError error() {
            return new com.example.english_app_cdcntt.service.AiClient.GradingError(
                    "have went", "went", "Past simple, not present perfect.");
        }
    }

    private final PhraseDto dto = new PhraseDto(1L, "I have went to the store yesterday.",
            "I went to the store yesterday.", 8, Instant.parse("2026-10-01T00:00:00Z"),
            List.of(new GrammarErrorDto("have went", "went", "Past simple, not present perfect.")));

    private static Phrase phrase() {
        Phrase phrase = Phrase.create(user(USER_ID), "text", "corrected", 8);
        ReflectionTestUtils.setField(phrase, "id", 7L);
        return phrase;
    }

    private static User user(long id) {
        User user = User.create("alice", "hash");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @BeforeEach
    void setUp() {
        phraseService = new PhraseServiceImpl(
                phraseRepository, phraseTxService, gradingService, phraseMapper);
 lenient().when(gradingService.grade(anyString())).thenReturn(goodGrading);
 lenient().when(phraseTxService.save(anyLong(), anyString(), any(GradingResult.class)))
                .thenReturn(dto);
 lenient().when(phraseMapper.toDto(any(Phrase.class))).thenReturn(dto);
    }

    @Test
    @DisplayName("create: grade trước rồi persist qua tx, trả DTO của tx")
    void createGradesThenPersists() {
        PhraseDto result = phraseService.create(USER_ID, new PhraseForm("I have went to the store yesterday."));

        assertThat(result).isSameAs(dto);
        verify(gradingService).grade("I have went to the store yesterday.");
        verify(phraseTxService).save(USER_ID, "I have went to the store yesterday.", goodGrading);
    }

    @Test
    @DisplayName("create: AI báo không phải đoạn văn → 400, không persist")
    void createRejectsNonPhraseWithoutPersisting() {
        when(gradingService.grade(anyString()))
                .thenThrow(new InvalidPhraseException());

        assertThatThrownBy(() -> phraseService.create(USER_ID,
                new PhraseForm("just seven")))
                .isInstanceOf(InvalidPhraseException.class);
        verify(phraseTxService, never()).save(anyLong(), anyString(), any(GradingResult.class));
    }

    @Test
    @DisplayName("create: AI hỏng → 502, không persist")
    void createDoesNotPersistWhenAiFails() {
        when(gradingService.grade(anyString()))
                .thenThrow(new AiServiceException("AI request failed"));

        assertThatThrownBy(() -> phraseService.create(USER_ID,
                new PhraseForm("Some long enough text here.")))
                .isInstanceOf(AiServiceException.class);
        verify(phraseTxService, never()).save(anyLong(), anyString(), any(GradingResult.class));
    }

    @Test
    @DisplayName("list: map từng phrase (trong tx đọc lazy errors)")
    void listMapsEachPhrase() {
        when(phraseRepository.findByUser_IdOrderByIdAsc(USER_ID))
                .thenReturn(List.of(phrase(), phrase()));


        List<PhraseDto> result = phraseService.list(USER_ID);

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).isSameAs(dto);
    }

    @Test
    @DisplayName("delete: id của chính user → gọi tx xóa")
    void deleteOwnedPhrase() {
        phraseService.delete(USER_ID, 42L);
        verify(phraseTxService).delete(42L, USER_ID);
    }
}
