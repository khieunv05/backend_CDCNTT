package com.example.english_app_cdcntt.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.english_app_cdcntt.dto.GeneratedWordDto;
import com.example.english_app_cdcntt.dto.WordValueDto;
import com.example.english_app_cdcntt.entity.WordCache;
import com.example.english_app_cdcntt.entity.WordCacheValue;
import com.example.english_app_cdcntt.enums.Level;
import com.example.english_app_cdcntt.enums.PartOfSpeech;
import com.example.english_app_cdcntt.exception.AiServiceException;
import com.example.english_app_cdcntt.exception.InvalidWordException;
import com.example.english_app_cdcntt.mapper.WordCacheMapper;
import com.example.english_app_cdcntt.service.AiClient;
import com.example.english_app_cdcntt.service.WordCacheTxService;
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
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Plain unit tests for the §7 generate coordinator. No Spring, no DB: the cache transaction
 * helper and the AI adapter are Mockito mocks, so every branch (HIT, MISS, invalid word, AI
 * failure, UNIQUE race) is checked against the exact §4.1/§8.2 contract without an LLM.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GenerateServiceImplTest {

    private final WordCacheMapper wordCacheMapper = new WordCacheMapper();

    @Mock
    private AiClient aiClient;

    @Mock
    private WordCacheTxService wordCacheTxService;

    private GenerateServiceImpl generateService;

    @BeforeEach
    void setUp() {
        generateService = new GenerateServiceImpl(wordCacheTxService, aiClient, wordCacheMapper);
    }

    private AiClient.MeaningItem meaning(String vietnamese) {
        return new AiClient.MeaningItem(vietnamese, "a banana", "một quả chuối", "/bəˈnɑː.nə/",
                PartOfSpeech.NOUN);
    }

    private AiClient.GeneratedMeaning validMeaning() {
        return new AiClient.GeneratedMeaning(true, Level.B1, java.util.List.of(meaning("quả chuối")));
    }

    private WordCache cachedWord(String english) {
        WordCache cache = WordCache.create(english, Level.B1);
        cache.addValue(WordCacheValue.create("quả chuối", "a banana", "một quả chuối",
                "/bəˈnɑː.nə/", PartOfSpeech.NOUN));
        return cache;
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "abc123", "tiếng-Anh", "one two three four five six",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                    + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                    + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                    + "aaaaaa"})
    @DisplayName("không phải từ/cụm từ tiếng Anh hợp lệ (hoặc >5 từ) → 400, không đụng cache/AI")
    void rejectsNonSingleWord(String raw) {
        assertThatThrownBy(() -> generateService.generateWord(raw))
                .isInstanceOf(InvalidWordException.class);
        verifyNoInteractions(wordCacheTxService, aiClient);
    }

    @ParameterizedTest
    @ValueSource(strings = {"don't", "co-operate", "mother-in-law", "make up", "get along with"})
    @DisplayName("chấp nhận từ ghép, từ có apostrophe và cụm từ (phrasal verb)")
    void acceptsCompoundSpellings(String raw) {
        when(wordCacheTxService.findCached(anyString())).thenReturn(Optional.empty());
        when(aiClient.generateWordMeaning(anyString())).thenReturn(validMeaning());
        when(wordCacheTxService.saveNew(anyString(), any())).thenReturn(
                new GeneratedWordDto("x", Level.B1, java.util.List.of()));

        generateService.generateWord(raw);

        verify(aiClient).generateWordMeaning(raw.strip().toLowerCase(java.util.Locale.ROOT)
                .replaceAll("\\s+", " "));
    }

    @Test
    @DisplayName("normalize cụm từ: '  make   UP  ' gửi xuống AI/cache là 'make up' (strip + collapse spaces + lowercase)")
    void normalizesPhraseKeyBeforeLookup() {
        when(wordCacheTxService.findCached("make up")).thenReturn(Optional.empty());
        when(aiClient.generateWordMeaning("make up")).thenReturn(validMeaning());
        when(wordCacheTxService.saveNew(eq("make up"), any())).thenReturn(
                new GeneratedWordDto("make up", Level.B1, java.util.List.of()));

        generateService.generateWord("  make   UP  ");

        verify(wordCacheTxService).saveNew(eq("make up"), any());
    }

    @Test
    @DisplayName("normalize: '  BANANA  ' gửi xuống AI/cache là 'banana' (strip + lowercase)")
    void normalizesKeyBeforeLookup() {
        when(wordCacheTxService.findCached("banana")).thenReturn(Optional.empty());
        when(aiClient.generateWordMeaning("banana")).thenReturn(validMeaning());
        when(wordCacheTxService.saveNew(eq("banana"), any())).thenReturn(
                new GeneratedWordDto("banana", Level.B1, java.util.List.of()));

        generateService.generateWord("  BANANA  ");

        verify(wordCacheTxService).findCached("banana");
        verify(aiClient).generateWordMeaning("banana");
        verify(wordCacheTxService).saveNew(eq("banana"), any());
    }

    @Test
    @DisplayName("HIT: cache có sẵn → trả nghĩa đã lưu, KHÔNG gọi AI (§11)")
    void cacheHitNeverCallsAi() {
        WordCache cache = cachedWord("banana");
        when(wordCacheTxService.findCached("banana")).thenReturn(Optional.of(cache));

        GeneratedWordDto dto = generateService.generateWord("BANANA");

        assertThat(dto.english()).isEqualTo("banana");
        assertThat(dto.level()).isEqualTo(Level.B1);
        assertThat(dto.values()).hasSize(1);
        assertThat(dto.values().get(0).id()).isNull();
        verifyNoInteractions(aiClient);
        verify(wordCacheTxService, never()).saveNew(anyString(), any());
    }

    @Test
    @DisplayName("MISS: gọi AI ngoài tx rồi ghi cache đúng key chuẩn hóa")
    void missCallsAiThenSavesCache() {
        when(wordCacheTxService.findCached("banana")).thenReturn(Optional.empty());
        AiClient.GeneratedMeaning meaning = validMeaning();
        when(aiClient.generateWordMeaning("banana")).thenReturn(meaning);
        GeneratedWordDto saved = new GeneratedWordDto("banana", Level.B1,
                java.util.List.of(new WordValueDto(null, "quả chuối", "a banana",
                        "một quả chuối", "/bəˈnɑː.nə/", PartOfSpeech.NOUN)));
        when(wordCacheTxService.saveNew("banana", meaning)).thenReturn(saved);

        GeneratedWordDto dto = generateService.generateWord("  BANANA  ");

        assertThat(dto).isSameAs(saved);
        verify(wordCacheTxService).saveNew("banana", meaning);
    }

    @Test
    @DisplayName("AI trả validWord=false → 400, không ghi cache")
    void nonWordFromAiIsInvalid() {
        when(wordCacheTxService.findCached("asdfgh")).thenReturn(Optional.empty());
        when(aiClient.generateWordMeaning("asdfgh")).thenReturn(
                new AiClient.GeneratedMeaning(false, null, java.util.List.of()));

        assertThatThrownBy(() -> generateService.generateWord("asdfgh"))
                .isInstanceOf(InvalidWordException.class);
        verify(wordCacheTxService, never()).saveNew(anyString(), any());
    }

    @Test
    @DisplayName("AI lỗi (timeout/429/schema sai) → AiServiceException, không ghi cache")
    void aiFailurePropagatesWithoutSaving() {
        when(wordCacheTxService.findCached("banana")).thenReturn(Optional.empty());
        when(aiClient.generateWordMeaning("banana")).thenThrow(new AiServiceException("429"));

        assertThatThrownBy(() -> generateService.generateWord("banana"))
                .isInstanceOf(AiServiceException.class);
        verify(wordCacheTxService, never()).saveNew(anyString(), any());
    }

    @Test
    @DisplayName("Race UNIQUE(english): rollback xong mới đọc lại, hit thật → trả cache, không gọi save lần hai")
    void raceReturnsExistingRowAfterReRead() {
        when(wordCacheTxService.findCached("banana"))
                .thenReturn(Optional.empty())          // lần đầu MISS
                .thenReturn(Optional.of(cachedWord("banana"))); // đọc lại sau rollback
        when(aiClient.generateWordMeaning("banana")).thenReturn(validMeaning());
        when(wordCacheTxService.saveNew(anyString(), any()))
                .thenThrow(new DataIntegrityViolationException("uk_word_cache_english"));

        GeneratedWordDto dto = generateService.generateWord("banana");

        assertThat(dto.english()).isEqualTo("banana");
        assertThat(dto.values().get(0).id()).isNull();
        verify(wordCacheTxService).saveNew(anyString(), any());
    }

    @Test
    @DisplayName("Race mà đọc lại vẫn trống → AiServiceException (không tự bịa cache-hit)")
    void raceWithEmptyReReadIsInfraError() {
        when(wordCacheTxService.findCached("banana"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.empty());
        when(aiClient.generateWordMeaning("banana")).thenReturn(validMeaning());
        when(wordCacheTxService.saveNew(anyString(), any()))
                .thenThrow(new DataIntegrityViolationException("uk_word_cache_english"));

        assertThatThrownBy(() -> generateService.generateWord("banana"))
                .isInstanceOf(AiServiceException.class)
                .hasMessage(AiServiceException.MESSAGE);
        // the single saveNew attempt is the race itself — it must happen exactly once
        verify(wordCacheTxService, times(1)).saveNew(anyString(), any());
    }
}
