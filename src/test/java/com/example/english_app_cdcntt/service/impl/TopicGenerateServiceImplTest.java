package com.example.english_app_cdcntt.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.english_app_cdcntt.dto.GeneratedWordDto;
import com.example.english_app_cdcntt.dto.WordDto;
import com.example.english_app_cdcntt.entity.User;
import com.example.english_app_cdcntt.entity.Word;
import com.example.english_app_cdcntt.entity.WordCache;
import com.example.english_app_cdcntt.entity.WordCacheValue;
import com.example.english_app_cdcntt.enums.Level;
import com.example.english_app_cdcntt.enums.PartOfSpeech;
import com.example.english_app_cdcntt.exception.AiServiceException;
import com.example.english_app_cdcntt.exception.InvalidTopicException;
import com.example.english_app_cdcntt.exception.InvalidWordException;
import com.example.english_app_cdcntt.form.WordForm;
import com.example.english_app_cdcntt.form.WordValueForm;
import com.example.english_app_cdcntt.mapper.WordCacheMapper;
import com.example.english_app_cdcntt.repository.WordRepository;
import com.example.english_app_cdcntt.service.AiClient;
import com.example.english_app_cdcntt.service.TopicTxService;
import com.example.english_app_cdcntt.service.WordCacheTxService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
 * Plain unit tests for the §7.1 split flow. Step 1 (generate-topic) proposes and caches only —
 * the notebook and the tx service are never touched; step 2 (confirm) trusts the WordForm
 * payloads the user picked (user decision 2026-10-05: no cache lookup, no AI call) — it only
 * normalizes, validates the stored shape and dedupes, then hands the forms to the one write
 * transaction.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TopicGenerateServiceImplTest {

    @Mock
    private AiClient aiClient;

    @Mock
    private WordRepository wordRepository;

    @Mock
    private WordCacheTxService wordCacheTxService;

    @Mock
    private TopicTxService topicTxService;

    private final WordCacheMapper wordCacheMapper = new WordCacheMapper();

    private TopicGenerateServiceImpl topicGenerateService;

    @BeforeEach
    void setUp() {
        topicGenerateService = new TopicGenerateServiceImpl(aiClient, wordRepository,
                wordCacheTxService, wordCacheMapper, topicTxService);
        when(wordRepository.findByUser_IdOrderByIdAsc(1L)).thenReturn(List.of());
        when(wordCacheTxService.findCached(anyString())).thenReturn(Optional.empty());
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

    private WordCache cachedWord(String english) {
        WordCache cache = WordCache.create(english, Level.A2);
        cache.addValue(WordCacheValue.create(
                "nghĩa đã lưu", "cached example", "ví dụ đã lưu", "/ipa/", PartOfSpeech.NOUN));
        return cache;
    }

    // ---------- step 1: POST /api/words/generate-topic ----------

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t\n"})
    @DisplayName("chủ đề rỗng sau chuẩn hóa → 400, không đụng AI/DB (contract @NotBlank dư trên controller)")
    void blankTopicIsRejectedBeforeAnyCall(String raw) {
        assertThatThrownBy(() -> topicGenerateService.generateTopicWords(1L, raw))
                .isInstanceOf(InvalidTopicException.class);
        verifyNoInteractions(aiClient, wordRepository, wordCacheTxService, topicTxService);
    }

    @Test
    @DisplayName("AI từ chối chủ đề (validTopic=false) → 400, không lấy exclude, không cache")
    void aiRejectedTopicIs400() {
        when(aiClient.checkTopic("fjaskdf")).thenReturn(false);

        assertThatThrownBy(() -> topicGenerateService.generateTopicWords(1L, "fjaskdf"))
                .isInstanceOf(InvalidTopicException.class)
                .hasMessage(InvalidTopicException.MESSAGE);
        verify(aiClient, never()).generateTopicWords(anyString(), anyList());
        verifyNoInteractions(wordRepository, wordCacheTxService, topicTxService);
    }

    @Test
    @DisplayName("AI lỗi (timeout/schema sai) → AiServiceException 502, không cache, không vào sổ")
    void aiFailurePropagates() {
        when(aiClient.checkTopic("Travel")).thenReturn(true);
        when(aiClient.generateTopicWords(anyString(), anyList()))
                .thenThrow(new AiServiceException("429"));

        assertThatThrownBy(() -> topicGenerateService.generateTopicWords(1L, "Travel"))
                .isInstanceOf(AiServiceException.class);
        verifyNoInteractions(wordCacheTxService, topicTxService);
    }

    @Test
    @DisplayName("happy path bước 1: chuẩn hóa + exclude theo sổ, MISS lưu cache từ đề xuất của AI, HIT dùng lại — KHÔNG tạo Word")
    void happyPathProposesAndCachesOnly() {
        when(aiClient.checkTopic("Du lịch")).thenReturn(true);
        when(wordRepository.findByUser_IdOrderByIdAsc(1L))
                .thenReturn(List.of(ownedWord("travel"), ownedWord("hotel")));
        AiClient.TopicWord airport = topicWord("airport");
        when(aiClient.generateTopicWords("Du lịch", List.of("travel", "hotel")))
                .thenReturn(List.of(airport, topicWord("luggage")));
        GeneratedWordDto airportDto = new GeneratedWordDto("airport", Level.B1, List.of());
        when(wordCacheTxService.saveNew(eq("airport"), any(AiClient.GeneratedMeaning.class)))
                .thenReturn(airportDto);
        when(wordCacheTxService.findCached("luggage"))
                .thenReturn(Optional.of(cachedWord("luggage")));

        List<GeneratedWordDto> result = topicGenerateService.generateTopicWords(1L, "  Du   lịch  ");

        // the AI sees the collapsed topic and the stored exclude list
        verify(aiClient).generateTopicWords("Du lịch", List.of("travel", "hotel"));
        // MISS → the AI's own proposal is persisted as the shared cache row
        verify(wordCacheTxService).saveNew("airport",
                new AiClient.GeneratedMeaning(true, Level.B1, airport.values()));
        // HIT → the shared cache row is reused, no second write
        verify(wordCacheTxService, never()).saveNew(eq("luggage"), any(AiClient.GeneratedMeaning.class));
        assertThat(result).containsExactly(airportDto,
                wordCacheMapper.toGenerated(cachedWord("luggage")));
        // step 1 NEVER touches the notebook and never runs the single-word generate flow
        verifyNoInteractions(topicTxService);
    }

    @Test
    @DisplayName("đề xuất sai shape / trùng trong lô / đã có trong sổ bị bỏ qua — không hủy cả lô")
    void skipsWrongShapeDuplicateAndOwned() {
        when(aiClient.checkTopic("Food")).thenReturn(true);
        when(wordRepository.findByUser_IdOrderByIdAsc(1L)).thenReturn(List.of(ownedWord("hotel")));
        when(aiClient.generateTopicWords("Food", List.of("hotel"))).thenReturn(List.of(
                topicWord("Airport!"), // wrong stored shape
                topicWord("restaurant"),
                topicWord("Restaurant"), // duplicate key after normalize
                topicWord("hotel"))); // already in the notebook
        GeneratedWordDto restaurantDto = new GeneratedWordDto("restaurant", Level.B1, List.of());
        when(wordCacheTxService.saveNew(eq("restaurant"), any(AiClient.GeneratedMeaning.class)))
                .thenReturn(restaurantDto);

        List<GeneratedWordDto> result = topicGenerateService.generateTopicWords(1L, "Food");

        assertThat(result).containsExactly(restaurantDto);
        verify(wordCacheTxService, times(1)).saveNew(anyString(), any(AiClient.GeneratedMeaning.class));
    }

    @Test
    @DisplayName("sổ từ trống → exclude rỗng vẫn gọi AI sinh từ (không chặn)")
    void emptyNotebookStillProposes() {
        when(aiClient.checkTopic("Food")).thenReturn(true);
        when(aiClient.generateTopicWords("Food", List.of())).thenReturn(List.of(topicWord("restaurant")));
        when(wordCacheTxService.saveNew(eq("restaurant"), any(AiClient.GeneratedMeaning.class)))
                .thenReturn(new GeneratedWordDto("restaurant", Level.B1, List.of()));

        topicGenerateService.generateTopicWords(1L, "Food");

        verify(aiClient).generateTopicWords("Food", List.of());
    }

    // ---------- step 2: POST /api/words/generate-topic/confirm ----------

    private WordForm wordForm(String english, Level level, String vietnamese) {
        return new WordForm(english, level, List.of(new WordValueForm(
                null, vietnamese, "example", "dịch", "/ipa/", PartOfSpeech.NOUN)));
    }

    @Test
    @DisplayName("happy path bước 2: normalize + gộp trùng giữ form đầu, KHÔNG đụng cache/AI, 1 tx thêm vào sổ")
    void confirmAddsWordFormsInOneTx() {
        WordDto airport = new WordDto(10L, "airport", Level.B1, 0,
                Instant.parse("2026-01-15T08:00:00Z"), null, null, List.of());
        WordDto hotel = new WordDto(11L, "hotel", Level.A2, 0,
                Instant.parse("2026-01-15T08:00:00Z"), null, null, List.of());
        // duplicates after normalize keep the FIRST form (its level/values win downstream)
        when(topicTxService.addWords(1L, List.of(
                wordForm("  Airport  ", Level.B1, "sân bay"),
                wordForm("hotel", Level.A2, "khách sạn"))))
                .thenReturn(List.of(airport, hotel));

        List<WordDto> result = topicGenerateService.confirmTopicWords(
                1L, List.of(
                        wordForm("  Airport  ", Level.B1, "sân bay"),
                        wordForm("airport", Level.C1, "phi trường"),
                        wordForm("hotel", Level.A2, "khách sạn")));

        // deduplicated after normalize; the payload is trusted: no cache lookup, no AI call
        verify(topicTxService).addWords(1L, List.of(
                wordForm("  Airport  ", Level.B1, "sân bay"),
                wordForm("hotel", Level.A2, "khách sạn")));
        verifyNoInteractions(aiClient, wordRepository, wordCacheTxService);
        assertThat(result).containsExactly(airport, hotel);
    }

    @Test
    @DisplayName("confirm: từ bấm vào sai shape → InvalidWordException 400, không vào sổ")
    void confirmInvalidWordFailsFast() {
        assertThatThrownBy(() -> topicGenerateService.confirmTopicWords(
                1L, List.of(wordForm("not a word!", Level.A2, "sai"))))
                .isInstanceOf(InvalidWordException.class)
                .hasMessage("Từ hoặc cụm từ gửi lên không phải một từ tiếng Anh hợp lệ");
        verifyNoInteractions(topicTxService);
    }

    @Test
    @DisplayName("confirm: khoảng trắng → từ rỗng sau chuẩn hóa → InvalidWordException 400")
    void confirmBlankWordIsInvalid() {
        assertThatThrownBy(() -> topicGenerateService.confirmTopicWords(
                1L, List.of(wordForm("   ", Level.A2, "trống"))))
                .isInstanceOf(InvalidWordException.class);
        verifyNoInteractions(topicTxService);
    }
}
