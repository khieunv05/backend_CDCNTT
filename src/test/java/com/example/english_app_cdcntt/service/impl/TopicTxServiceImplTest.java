package com.example.english_app_cdcntt.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.english_app_cdcntt.dto.WordDto;
import com.example.english_app_cdcntt.entity.User;
import com.example.english_app_cdcntt.entity.Word;
import com.example.english_app_cdcntt.entity.WordValue;
import com.example.english_app_cdcntt.enums.Level;
import com.example.english_app_cdcntt.enums.PartOfSpeech;
import com.example.english_app_cdcntt.repository.UserRepository;
import com.example.english_app_cdcntt.repository.WordRepository;
import com.example.english_app_cdcntt.service.AiClient;
import com.example.english_app_cdcntt.service.WordCacheTxService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Plain unit tests for the §13.12 one-write-transaction of generate-topic: a bad/duplicate/
 * owned word is skipped without cancelling the batch, cache-first reuse, and the Word shape
 * built inside the batch (reviewCount=0, nextReview = created date).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TopicTxServiceImplTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-01-15T08:00:00Z");

    @Mock
    private WordRepository wordRepository;

    @Mock
    private WordCacheTxService wordCacheTxService;

    @Mock
    private UserRepository userRepository;

    private TopicTxServiceImpl topicTxService;

    private final AtomicLong idSeq = new AtomicLong(0L);

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(FIXED_NOW, ZoneId.of("UTC"));
        topicTxService = new TopicTxServiceImpl(wordRepository, wordCacheTxService,
                userRepository, clock);
        when(userRepository.getReferenceById(1L)).thenReturn(User.create("alice", "hash"));
        // WordMapper.toDto sorts WordValue by id — assign ids like the flush would (§6.2)
        when(wordRepository.saveAndFlush(any(Word.class))).thenAnswer(invocation -> {
            Word word = invocation.getArgument(0);
            ReflectionTestUtils.setField(word, "id", idSeq.incrementAndGet());
            long valueSeq = 0L;
            for (WordValue value : word.getWordValues()) {
                ReflectionTestUtils.setField(value, "id", valueSeq++);
            }
            return word;
        });
    }

    private AiClient.TopicWord topicWord(String english) {
        return new AiClient.TopicWord(english, Level.B1, List.of(new AiClient.MeaningItem(
                "nghĩa", "example", "dịch", "/ipa/", PartOfSpeech.NOUN)));
    }

    private com.example.english_app_cdcntt.entity.WordCache cachedWord(String english) {
        com.example.english_app_cdcntt.entity.WordCache cache =
                com.example.english_app_cdcntt.entity.WordCache.create(english, Level.A2);
        cache.addValue(com.example.english_app_cdcntt.entity.WordCacheValue.create(
                "nghĩa cache", "example", "dịch", "/ipa/", PartOfSpeech.NOUN));
        return cache;
    }

    @Test
    @DisplayName("MISS cache: tạo word_cache từ đề xuất AI rồi tạo Word mới (reviewCount=0, due=now)")
    void missCreatesCacheThenNotebookWord() {
        when(wordRepository.existsByUser_IdAndEnglish(1L, "airport")).thenReturn(false);
        when(wordCacheTxService.findCached("airport")).thenReturn(Optional.empty());

        List<WordDto> result = topicTxService.saveBatch(1L, List.of(topicWord("airport")));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).english()).isEqualTo("airport");
        assertThat(result.get(0).reviewCount()).isZero();
        assertThat(result.get(0).nextReview()).isEqualTo(FIXED_NOW);
        verify(wordCacheTxService).saveNew(eq("airport"), any(AiClient.GeneratedMeaning.class));
    }

    @Test
    @DisplayName("HIT cache: dùng level + nghĩa của word_cache (A2), KHÔNG ghi lại cache")
    void hitReusesCacheWithoutRewriting() {
        when(wordRepository.existsByUser_IdAndEnglish(1L, "airport")).thenReturn(false);
        when(wordCacheTxService.findCached("airport")).thenReturn(Optional.of(cachedWord("airport")));

        List<WordDto> result = topicTxService.saveBatch(1L, List.of(topicWord("airport")));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).level()).isEqualTo(Level.A2);
        assertThat(result.get(0).values().get(0).vietnamese()).isEqualTo("nghĩa cache");
        verify(wordCacheTxService, never()).saveNew(anyString(), any());
    }

    @Test
    @DisplayName("từ sai shape (rác có dấu, >5 từ) bị BỎ, không hủy cả lô (§13.12)")
    void malformedWordIsSkippedNotBatchCancelled() {
        when(wordRepository.existsByUser_IdAndEnglish(eq(1L), anyString())).thenReturn(false);
        when(wordCacheTxService.findCached("good-word")).thenReturn(Optional.empty());

        List<WordDto> result = topicTxService.saveBatch(1L, List.of(
                topicWord("TỪ CÓ DẤU"), topicWord("a b c d e f"), topicWord("good-word")));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).english()).isEqualTo("good-word");
    }

    @Test
    @DisplayName("2 từ trùng nhau (khác hoa/thường) trong cùng lô → chỉ lưu 1 lần")
    void duplicateInsideBatchIsSkipped() {
        when(wordRepository.existsByUser_IdAndEnglish(1L, "airport")).thenReturn(false);
        when(wordCacheTxService.findCached("airport")).thenReturn(Optional.empty());

        List<WordDto> result =
                topicTxService.saveBatch(1L, List.of(topicWord("airport"), topicWord("Airport")));

        assertThat(result).hasSize(1);
        verify(wordRepository, times(1)).saveAndFlush(any(Word.class));
    }

    @Test
    @DisplayName("từ đã có trong sổ → bỏ qua, lô tiếp tục, không ghi cache cho từ đó (act-20 bước 3)")
    void alreadyOwnedWordIsSkipped() {
        when(wordRepository.existsByUser_IdAndEnglish(1L, "travel")).thenReturn(true);
        when(wordRepository.existsByUser_IdAndEnglish(1L, "airport")).thenReturn(false);
        when(wordCacheTxService.findCached("airport")).thenReturn(Optional.empty());

        List<WordDto> result =
                topicTxService.saveBatch(1L, List.of(topicWord("travel"), topicWord("airport")));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).english()).isEqualTo("airport");
        verify(wordCacheTxService, never()).saveNew(eq("travel"), any());
    }

    @Test
    @DisplayName("lô rỗng → trả rỗng, không đụng repo/cache")
    void emptyBatchReturnsEmptyList() {
        List<WordDto> result = topicTxService.saveBatch(1L, List.of());

        assertThat(result).isEmpty();
        verify(wordCacheTxService, never()).findCached(anyString());
    }

    @Test
    @DisplayName("mọi từ đều bị loại (sai shape + đã có) → trả rỗng, không exception")
    void batchWhereEverythingIsFilteredReturnsEmpty() {
        when(wordRepository.existsByUser_IdAndEnglish(eq(1L), anyString())).thenReturn(true);

        List<WordDto> result =
                topicTxService.saveBatch(1L, List.of(topicWord("TỪ CÓ DẤU"), topicWord("travel")));

        assertThat(result).isEmpty();
        verify(wordRepository, never()).saveAndFlush(any(Word.class));
    }
}
