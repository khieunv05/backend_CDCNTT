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
import com.example.english_app_cdcntt.entity.WordCache;
import com.example.english_app_cdcntt.entity.WordCacheValue;
import com.example.english_app_cdcntt.entity.WordValue;
import com.example.english_app_cdcntt.enums.Level;
import com.example.english_app_cdcntt.enums.PartOfSpeech;
import com.example.english_app_cdcntt.repository.UserRepository;
import com.example.english_app_cdcntt.repository.WordRepository;
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
 * Plain unit tests for the §13.12 one-write-transaction of generate-topic CONFIRM (step 2 of
 * the split flow): every key arrives with its cache row guaranteed, a bad/duplicate/owned word
 * is skipped without cancelling the batch, and the Word shape built inside the batch is
 * reviewCount=0 with nextReview at the created instant (due immediately, act-20 step 5).
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
        // Word's constructor requires a real user (Objects.requireNonNull)
        when(userRepository.getReferenceById(1L)).thenReturn(User.create("alice", "hash"));
        when(wordRepository.saveAndFlush(any(Word.class))).thenAnswer(invocation -> {
            Word word = invocation.getArgument(0);
            ReflectionTestUtils.setField(word, "id", idSeq.incrementAndGet());
            // WordMapper.toDto sorts values by id — mimic the persistence ids the tx would get
            long valueId = 1000L;
            for (WordValue value : word.getWordValues()) {
                ReflectionTestUtils.setField(value, "id", valueId++);
            }
            return word;
        });
        when(wordRepository.existsByUser_IdAndEnglish(eq(1L), anyString())).thenReturn(false);
    }

    private WordCache cachedWord(String english) {
        WordCache cache = WordCache.create(english, Level.A2);
        cache.addValue(WordCacheValue.create(
                "nghĩa đã lưu", "cached example", "ví dụ đã lưu", "/ipa/", PartOfSpeech.NOUN));
        cache.addValue(WordCacheValue.create(
                "nghĩa thứ hai", "example 2", "dịch 2", "/ipa2/", PartOfSpeech.VERB));
        return cache;
    }

    @Test
    @DisplayName("happy path: mỗi key được confirm tạo 1 Word từ cache — reviewCount=0, nextReview=ngày tạo, đủ values")
    void addsWordsFromCacheInOrder() {
        when(wordCacheTxService.findCached("airport")).thenReturn(Optional.of(cachedWord("airport")));
        when(wordCacheTxService.findCached("hotel"))
                .thenReturn(Optional.of(WordCache.create("hotel", Level.B1)));

        List<WordDto> result = topicTxService.addWordsFromCache(1L, List.of("airport", "hotel"));

        assertThat(result).hasSize(2);
        WordDto airport = result.get(0);
        assertThat(airport.english()).isEqualTo("airport");
        assertThat(airport.level()).isEqualTo(Level.A2);
        assertThat(airport.reviewCount()).isZero();
        assertThat(airport.nextReview()).isEqualTo(FIXED_NOW); // due ngay — act-20 step 5
        assertThat(airport.values()).hasSize(2);
        assertThat(airport.values().get(0).vietnamese()).isEqualTo("nghĩa đã lưu");
        assertThat(result.get(1).english()).isEqualTo("hotel"); // cache B1, không values trong stub này
        assertThat(result.get(1).level()).isEqualTo(Level.B1);
    }

    @Test
    @DisplayName("cache MISS trong tx (sự cố) → bỏ qua từ đó, không hủy cả lô, không save")
    void missingCacheIsSkippedDefensively() {
        when(wordCacheTxService.findCached(anyString())).thenReturn(Optional.empty());

        List<WordDto> result = topicTxService.addWordsFromCache(1L, List.of("airport"));

        assertThat(result).isEmpty();
        verify(wordRepository, never()).saveAndFlush(any(Word.class));
    }

    @Test
    @DisplayName("từ đã có trong sổ bị bỏ qua — lô vẫn chạy tiếp với từ sau")
    void skipsAlreadyOwnedWord() {
        when(wordRepository.existsByUser_IdAndEnglish(1L, "airport")).thenReturn(true);
        when(wordCacheTxService.findCached("hotel")).thenReturn(Optional.of(cachedWord("hotel")));

        List<WordDto> result = topicTxService.addWordsFromCache(1L, List.of("airport", "hotel"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).english()).isEqualTo("hotel");
        verify(wordCacheTxService, never()).findCached("airport");
    }

    @Test
    @DisplayName("trùng lặp sau chuẩn hóa chỉ xử lý 1 lần")
    void dedupesKeysInsideTheBatch() {
        when(wordCacheTxService.findCached("airport")).thenReturn(Optional.of(cachedWord("airport")));

        List<WordDto> result = topicTxService.addWordsFromCache(
                1L, List.of("  Airport  ", "airport", "AIRPORT"));

        assertThat(result).hasSize(1);
        verify(wordCacheTxService, times(1)).findCached("airport");
    }

    @Test
    @DisplayName("key sai shape bị bỏ qua — không hủy cả lô")
    void skipsBadShapeKeys() {
        when(wordCacheTxService.findCached("hotel")).thenReturn(Optional.of(cachedWord("hotel")));

        List<WordDto> result = topicTxService.addWordsFromCache(
                1L, List.of("Not a word!", "hotel"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).english()).isEqualTo("hotel");
    }
}
