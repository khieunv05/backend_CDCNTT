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
import com.example.english_app_cdcntt.form.WordForm;
import com.example.english_app_cdcntt.form.WordValueForm;
import com.example.english_app_cdcntt.repository.UserRepository;
import com.example.english_app_cdcntt.repository.WordRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
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
 * the split flow). Per the user decision of 2026-10-05 the rows are built straight from the
 * {@link WordForm} payloads the caller validated — no word_cache lookup, no AI call. A bad/
 * duplicate/owned word is skipped without cancelling the batch, and the Word shape built
 * inside the batch is reviewCount=0 with nextReview at the created instant (due immediately,
 * act-20 step 5).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TopicTxServiceImplTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-01-15T08:00:00Z");

    @Mock
    private WordRepository wordRepository;

    @Mock
    private UserRepository userRepository;

    private TopicTxServiceImpl topicTxService;

    private final AtomicLong idSeq = new AtomicLong(0L);

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(FIXED_NOW, ZoneId.of("UTC"));
        topicTxService = new TopicTxServiceImpl(wordRepository, userRepository, clock);
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

    private WordValueForm meaning(String vietnamese, PartOfSpeech pos) {
        return new WordValueForm(null, vietnamese, "example", "dịch", "/ipa/", pos);
    }

    private WordForm wordForm(String english, Level level, WordValueForm... values) {
        return new WordForm(english, level, List.of(values));
    }

    @Test
    @DisplayName("happy path: mỗi WordForm được confirm tạo 1 Word ĐÚNG data form — reviewCount=0, nextReview=ngày tạo, đủ values")
    void addsWordFormsInOrder() {
        List<WordDto> result = topicTxService.addWords(1L, List.of(
                wordForm("airport", Level.A2,
                        meaning("sân bay", PartOfSpeech.NOUN),
                        meaning("đáp máy bay", PartOfSpeech.VERB)),
                wordForm("hotel", Level.B1, meaning("khách sạn", PartOfSpeech.NOUN))));

        assertThat(result).hasSize(2);
        WordDto airport = result.get(0);
        assertThat(airport.english()).isEqualTo("airport");
        assertThat(airport.level()).isEqualTo(Level.A2); // from the form, not any cache row
        assertThat(airport.reviewCount()).isZero();
        assertThat(airport.nextReview()).isEqualTo(FIXED_NOW); // due ngay — act-20 step 5
        assertThat(airport.values()).hasSize(2);
        assertThat(airport.values().get(0).vietnamese()).isEqualTo("sân bay");
        assertThat(airport.values().get(0).partOfSpeech()).isEqualTo(PartOfSpeech.NOUN);
        assertThat(result.get(1).english()).isEqualTo("hotel");
        assertThat(result.get(1).level()).isEqualTo(Level.B1);
        assertThat(result.get(1).values()).hasSize(1);
    }

    @Test
    @DisplayName("từ đã có trong sổ bị bỏ qua — lô vẫn chạy tiếp với từ sau")
    void skipsAlreadyOwnedWord() {
        when(wordRepository.existsByUser_IdAndEnglish(1L, "airport")).thenReturn(true);

        List<WordDto> result = topicTxService.addWords(1L, List.of(
                wordForm("airport", Level.A2, meaning("sân bay", PartOfSpeech.NOUN)),
                wordForm("hotel", Level.B1, meaning("khách sạn", PartOfSpeech.NOUN))));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).english()).isEqualTo("hotel");
        verify(wordRepository, times(1)).saveAndFlush(any(Word.class));
    }

    @Test
    @DisplayName("trùng lặp sau chuẩn hóa chỉ xử lý 1 lần — data của form XUẤT HIỆN ĐẦU wins")
    void dedupesFormsInsideTheBatchAndFirstWins() {
        List<WordDto> result = topicTxService.addWords(1L, List.of(
                wordForm("  Airport  ", Level.A2, meaning("sân bay", PartOfSpeech.NOUN)),
                wordForm("airport", Level.B1, meaning("phi trường", PartOfSpeech.NOUN)),
                wordForm("AIRPORT", Level.C1, meaning("sân bay nữa", PartOfSpeech.NOUN))));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).english()).isEqualTo("airport");
        assertThat(result.get(0).level()).isEqualTo(Level.A2);
        assertThat(result.get(0).values().get(0).vietnamese()).isEqualTo("sân bay");
        verify(wordRepository, times(1)).saveAndFlush(any(Word.class));
    }

    @Test
    @DisplayName("form sai shape bị bỏ qua — không hủy cả lô (lớp bảo vệ sau validation controller)")
    void skipsBadShapeForms() {
        List<WordDto> result = topicTxService.addWords(1L, List.of(
                wordForm("Not a word!", Level.A2, meaning("sai", PartOfSpeech.NOUN)),
                wordForm("hotel", Level.B1, meaning("khách sạn", PartOfSpeech.NOUN))));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).english()).isEqualTo("hotel");
    }

    @Test
    @DisplayName("english rỗng sau chuẩn hóa bị bỏ qua — không hủy cả lô, không save")
    void skipsBlankEnglishDefensively() {
        List<WordDto> result = topicTxService.addWords(1L, List.of(
                wordForm("   ", Level.A2, meaning("trống", PartOfSpeech.NOUN))));

        assertThat(result).isEmpty();
        verify(wordRepository, never()).saveAndFlush(any(Word.class));
    }
}
