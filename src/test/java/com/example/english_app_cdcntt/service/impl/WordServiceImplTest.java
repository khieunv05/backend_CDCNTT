package com.example.english_app_cdcntt.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.english_app_cdcntt.dto.DueCountResponse;
import com.example.english_app_cdcntt.dto.WordDto;
import com.example.english_app_cdcntt.dto.WordValueDto;
import com.example.english_app_cdcntt.entity.User;
import com.example.english_app_cdcntt.entity.Word;
import com.example.english_app_cdcntt.entity.WordValue;
import com.example.english_app_cdcntt.enums.Level;
import com.example.english_app_cdcntt.enums.PartOfSpeech;
import com.example.english_app_cdcntt.exception.DuplicateWordException;
import com.example.english_app_cdcntt.exception.InvalidRequestException;
import com.example.english_app_cdcntt.exception.OwnershipDeniedException;
import com.example.english_app_cdcntt.form.WordForm;
import com.example.english_app_cdcntt.form.WordValueForm;
import com.example.english_app_cdcntt.repository.UserRepository;
import com.example.english_app_cdcntt.repository.WordRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Plain unit tests for the §6.2 word rules. No Spring context, no database: repositories are
 * Mockito mocks and the {@link Clock} is fixed, so {@code now} — and therefore the due boundary and
 * the SRS state written on create — is deterministic.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WordServiceImplTest {

    private static final Long USER_ID = 7L;
    private static final Long WORD_ID = 42L;
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private WordRepository wordRepository;

    @Mock
    private UserRepository userRepository;

    private WordServiceImpl wordService;

    @BeforeEach
    void setUp() {
        wordService = new WordServiceImpl(wordRepository, userRepository, FIXED_CLOCK);
    }

    private static User user(long id) {
        User user = User.create("alice", "hash");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static WordValue value(long id, String vietnamese) {
        WordValue value = WordValue.create(vietnamese, null, null, null, null);
        ReflectionTestUtils.setField(value, "id", id);
        return value;
    }

    private static Word word(long id, String english, WordValue... values) {
        Word word = Word.create(user(USER_ID), english, NOW);
        for (WordValue value : values) {
            word.addValue(value);
        }
        ReflectionTestUtils.setField(word, "id", id);
        return word;
    }

    private static WordForm form(String english) {
        return new WordForm(english, null, List.of(new WordValueForm(null, "xin chào", null, null, null, null)));
    }

    /** Emulates IDENTITY generation so the mapper sees assigned ids after saveAndFlush. */
    private static Answer<Word> assignIds() {
        return invocation -> {
            Word word = invocation.getArgument(0);
            long nextId = 100L;
            for (WordValue value : word.getWordValues()) {
                if (value.getId() == null) {
                    ReflectionTestUtils.setField(value, "id", nextId++);
                }
            }
            return word;
        };
    }

    @Test
    @DisplayName("listWords maps the notebook query and orders meanings by id")
    void listWordsMapsNotebook() {
        when(wordRepository.findByUser_IdOrderByIdAsc(USER_ID))
                .thenReturn(List.of(word(WORD_ID, "hello", value(2L, "chào"), value(1L, "xin chào"))));

        List<WordDto> words = wordService.listWords(USER_ID);

        assertThat(words).hasSize(1);
        assertThat(words.get(0).english()).isEqualTo("hello");
        assertThat(words.get(0).values()).extracting(WordValueDto::id).containsExactly(1L, 2L);
        verify(wordRepository).findByUser_IdOrderByIdAsc(USER_ID);
    }

    @Test
    @DisplayName("countDue counts nextReview <= now taken from the injected clock")
    void countDueUsesInjectedClock() {
        when(wordRepository.countByUser_IdAndNextReviewLessThanEqual(USER_ID, NOW)).thenReturn(3L);

        DueCountResponse response = wordService.countDue(USER_ID);

        assertThat(response.dueCount()).isEqualTo(3L);
    }

    @Test
    @DisplayName("createWord rejects a form carrying meaning ids before touching the database")
    void createRejectsMeaningIds() {
        WordForm form = new WordForm("hello", null,
                List.of(new WordValueForm(1L, "xin chào", null, null, null, null)));

        assertThatThrownBy(() -> wordService.createWord(USER_ID, form))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Không được gửi ID nghĩa khi thêm từ");
        verifyNoInteractions(wordRepository, userRepository);
    }

    @Test
    @DisplayName("createWord answers the duplicate message for an already normalized key")
    void createRejectsDuplicateKey() {
        when(wordRepository.existsByUser_IdAndEnglish(USER_ID, "hello")).thenReturn(true);

        assertThatThrownBy(() -> wordService.createWord(USER_ID, form("  HeLLo  ")))
                .isInstanceOf(DuplicateWordException.class)
                .hasMessage("Từ này đã có trong sổ của bạn");
        verify(wordRepository, never()).saveAndFlush(any(Word.class));
    }

    @Test
    @DisplayName("createWord stores the normalized key, reviewCount 0 and nextReview = now")
    void createStoresNormalizedKeyAndSrsState() {
        when(userRepository.getReferenceById(USER_ID)).thenReturn(user(USER_ID));
        when(wordRepository.saveAndFlush(any(Word.class))).thenAnswer(assignIds());

        WordDto dto = wordService.createWord(USER_ID, form("  HELLO  "));

        assertThat(dto.english()).isEqualTo("hello");
        assertThat(dto.reviewCount()).isZero();
        assertThat(dto.nextReview()).isEqualTo(NOW);
        ArgumentCaptor<Word> captor = ArgumentCaptor.forClass(Word.class);
        verify(wordRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getUser().getId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().getWordValues()).hasSize(1);
    }

    @Test
    @DisplayName("updateWord answers 403 for a word the user does not own, before the duplicate check")
    void updateForeignWordIsForbidden() {
        when(wordRepository.findOwnedForUpdate(WORD_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> wordService.updateWord(USER_ID, WORD_ID, form("hello")))
                .isInstanceOf(OwnershipDeniedException.class)
                .hasMessage("Không có quyền sửa từ này");
        verify(wordRepository, never()).existsByUser_IdAndEnglish(any(), anyString());
    }

    @Test
    @DisplayName("updateWord answers 409 when renaming onto another word's normalized key")
    void updateRejectsDuplicateRename() {
        when(wordRepository.findOwnedForUpdate(WORD_ID, USER_ID))
                .thenReturn(Optional.of(word(WORD_ID, "hello", value(1L, "xin chào"))));
        when(wordRepository.existsByUser_IdAndEnglish(USER_ID, "other")).thenReturn(true);

        assertThatThrownBy(() -> wordService.updateWord(USER_ID, WORD_ID, form("other")))
                .isInstanceOf(DuplicateWordException.class)
                .hasMessage("Từ này đã có trong sổ của bạn");
    }

    @Test
    @DisplayName("keeping the same key does not check uniqueness against the word itself")
    void updateSameKeySkipsDuplicateCheck() {
        Word existing = word(WORD_ID, "hello", value(1L, "xin chào"));
        when(wordRepository.findOwnedForUpdate(WORD_ID, USER_ID)).thenReturn(Optional.of(existing));
        when(wordRepository.saveAndFlush(existing)).thenAnswer(assignIds());

        WordDto dto = wordService.updateWord(USER_ID, WORD_ID, form(" Hello "));

        assertThat(dto.english()).isEqualTo("hello");
        verify(wordRepository, never()).existsByUser_IdAndEnglish(any(), anyString());
    }

// TESTS2
}