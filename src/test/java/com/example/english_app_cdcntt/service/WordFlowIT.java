package com.example.english_app_cdcntt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.english_app_cdcntt.dto.WordDto;
import com.example.english_app_cdcntt.dto.WordValueDto;
import com.example.english_app_cdcntt.entity.User;
import com.example.english_app_cdcntt.entity.Word;
import com.example.english_app_cdcntt.enums.PartOfSpeech;
import com.example.english_app_cdcntt.exception.DuplicateWordException;
import com.example.english_app_cdcntt.exception.InvalidRequestException;
import com.example.english_app_cdcntt.exception.OwnershipDeniedException;
import com.example.english_app_cdcntt.form.WordForm;
import com.example.english_app_cdcntt.form.WordValueForm;
import com.example.english_app_cdcntt.repository.UserRepository;
import com.example.english_app_cdcntt.repository.WordRepository;
import com.example.english_app_cdcntt.support.MySqlTestConfiguration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Phase 3 DoD — the Words CRUD + due-count flow of §6.2 end to end against the local MySQL 8.0.43
 * test database {@code lenglish_test}. Requires the {@code TEST_DB_*} environment variables (see
 * HUONG_DAN_TEST_MYSQL.md) and therefore runs only under failsafe ({@code .\mvnw.cmd clean
 * verify}), never in the harness {@code mvnw test}.
 *
 * <p>The class is deliberately NOT {@code @Transactional}: every service call commits — or, in the
 * rollback test, rolls back — in its own real transaction, so the tests observe actually committed
 * (or actually discarded) rows instead of a shared test transaction. {@link #cleanUp()} removes the
 * rows this class created; usernames and english keys are unique per run through {@link #RUN_ID}.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(MySqlTestConfiguration.class)
@DisplayName("Words CRUD flow on local MySQL")
class WordFlowIT {

    private static final String RUN_ID = Long.toHexString(System.nanoTime());

    @Autowired private WordService wordService;
    @Autowired private WordRepository wordRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbc;

    private Long aliceId;
    private Long bobId;

    @BeforeEach
    void createUsers() {
        aliceId = userRepository.saveAndFlush(User.create("flow-a-" + RUN_ID, "hash-alice")).getId();
        bobId = userRepository.saveAndFlush(User.create("flow-b-" + RUN_ID, "hash-bob")).getId();
    }

    @AfterEach
    void cleanUp() {
        // words first (users are FK-referenced); word_values follow the ON DELETE CASCADE.
        wordRepository.deleteAll(wordRepository.findByUser_IdOrderByIdAsc(aliceId));
        wordRepository.deleteAll(wordRepository.findByUser_IdOrderByIdAsc(bobId));
        userRepository.deleteById(aliceId);
        userRepository.deleteById(bobId);
    }

    @Test
    @DisplayName("create -> update -> delete writes, rewrites and removes word + meanings for real")
    void createUpdateDelete_fullFlow() {
        WordDto created = wordService.createWord(aliceId,
                form("  Hello  ", value("xin chào"), value("chào hỏi")));
        assertThat(created.id()).isNotNull();
        assertThat(created.english()).isEqualTo("hello"); // normalized key, not the raw input
        assertThat(created.reviewCount()).isZero();
        assertThat(created.nextReview()).isNotNull();
        assertThat(created.values()).extracting(WordValueDto::id).doesNotContainNull();
        assertThat(wordRows(aliceId)).isEqualTo(1);
        assertThat(meaningRows(created.id())).isEqualTo(2);

        // Rewrite: keep and edit the first meaning, drop the second, add a third, rename.
        WordValueDto first = created.values().get(0);
        WordDto updated = wordService.updateWord(aliceId, created.id(),
                form("  Hello World ",
                        new WordValueForm(first.id(), "xin chào bạn", "hello world",
                                "xin chào thế giới", "/həˈləʊ wɜːld/", PartOfSpeech.NOUN),
                        value("thế giới")));
        assertThat(updated.english()).isEqualTo("hello world");
        assertThat(updated.values()).hasSize(2);
        assertThat(updated.values().get(0).id()).isEqualTo(first.id()); // same row kept, not recreated
        assertThat(updated.values().get(0).vietnamese()).isEqualTo("xin chào bạn");
        // reviewCount and nextReview survive a meaning rewrite (§6.2 contract).
        assertThat(updated.reviewCount()).isZero();
        assertThat(updated.nextReview()).isEqualTo(created.nextReview());
        assertThat(meaningRows(created.id())).isEqualTo(2);
        Long droppedId = created.values().get(1).id();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM word_values WHERE id = ? AND word_id = ?",
                Integer.class, droppedId, created.id())).isZero(); // orphanRemoval deleted the row

        wordService.deleteWord(aliceId, created.id());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM words WHERE id = ?",
                Integer.class, created.id())).isZero();
        assertThat(meaningRows(created.id())).isZero(); // CASCADE removed the meanings
    }

    @Test
    @DisplayName("a fresh word is due immediately; countDue counts only the owner's due words")
    void dueCount_boundariesAndOwnership() {
        WordDto created = wordService.createWord(aliceId, form("hello", value("xin chào")));
        // nextReview = creation time, so the word is already due (LessThanEqual boundary §6.2:279).
        assertThat(wordService.countDue(aliceId).dueCount()).isEqualTo(1);

        Word word = wordRepository.findById(created.id()).orElseThrow();
        word.scheduleReview(3, Instant.now().plus(1, ChronoUnit.DAYS));
        wordRepository.saveAndFlush(word);
        assertThat(wordService.countDue(aliceId).dueCount()).isZero();

        wordService.createWord(bobId, form("hello", value("xin chào")));
        assertThat(wordService.countDue(aliceId).dueCount()).isZero(); // bob's words never leak in
        assertThat(wordService.countDue(bobId).dueCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("create with a duplicate english (any case/spaces) conflicts; other users unaffected")
    void create_duplicateEnglish_conflict() {
        wordService.createWord(aliceId, form("bank", value("ngân hàng")));
        assertThatThrownBy(() -> wordService.createWord(aliceId, form("  BANK ", value("ngân hàng"))))
                .isInstanceOf(DuplicateWordException.class);
        assertThat(wordService.listWords(aliceId)).hasSize(1);

        assertThatCode(() -> wordService.createWord(bobId, form("bank", value("ngân hàng"))))
                .doesNotThrowAnyException(); // uniqueness is per notebook
    }

    @Test
    @DisplayName("create rejecting a meaning id stores nothing — validation runs before any insert")
    void create_meaningIdOnCreate_rejectedNothingStored() {
        int wordsBefore = wordRows(aliceId);
        int meaningsBefore = jdbc.queryForObject("SELECT COUNT(*) FROM word_values", Integer.class);

        assertThatThrownBy(() -> wordService.createWord(aliceId,
                new WordForm("hello", null, List.of(
                        new WordValueForm(999L, "xin chào", null, null, null, PartOfSpeech.NOUN)))))
                .isInstanceOf(InvalidRequestException.class);

        assertThat(wordRows(aliceId)).isEqualTo(wordsBefore);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM word_values", Integer.class))
                .isEqualTo(meaningsBefore);
    }

    @Test
    @DisplayName("update with a meaning id from another word is rejected and leaves the word untouched")
    void update_foreignMeaningId_rejectedDataUntouched() {
        WordDto mine = wordService.createWord(aliceId, form("alpha", value("thứ nhất")));
        WordDto other = wordService.createWord(bobId, form("beta", value("của bob")));
        Long foreignId = other.values().get(0).id();

        assertThatThrownBy(() -> wordService.updateWord(aliceId, mine.id(),
                form("alpha", new WordValueForm(foreignId, "đánh cắp", null, null, null,
                        PartOfSpeech.NOUN))))
                .isInstanceOf(InvalidRequestException.class); // same message as unknown/repeated ids

        List<WordDto> after = wordService.listWords(aliceId);
        assertThat(after).hasSize(1);
        assertThat(after.get(0).values()).hasSize(1);
        assertThat(after.get(0).values().get(0).id()).isEqualTo(mine.values().get(0).id());
        assertThat(after.get(0).values().get(0).vietnamese()).isEqualTo("thứ nhất");
    }

    @Test
    @DisplayName("update renaming to an existing english conflicts and leaves the word untouched")
    void update_duplicateRename_rejectedDataUntouched() {
        WordDto alpha = wordService.createWord(aliceId, form("alpha", value("thứ nhất")));
        wordService.createWord(aliceId, form("beta", value("thứ hai")));

        assertThatThrownBy(() -> wordService.updateWord(aliceId, alpha.id(),
                form(" BETA ", value("đổi tên trùng"))))
                .isInstanceOf(DuplicateWordException.class);

        List<WordDto> after = wordService.listWords(aliceId);
        assertThat(after).hasSize(2);
        assertThat(after.get(0).english()).isEqualTo("alpha");
        assertThat(after.get(0).values()).extracting(WordValueDto::vietnamese)
                .containsExactly("thứ nhất");
    }

    @Test
    @DisplayName("a meaning row too long for its column fails the flush and rolls the whole update back")
    void update_meaningTooLong_fullRollback() {
        WordDto created = wordService.createWord(aliceId, form("gamma", value("nghĩa gốc")));
        WordValueDto original = created.values().get(0);
        // Service calls here bypass bean validation, so the oversized text reaches the DB constraint
        // word_values.vietnamese VARCHAR(1000) and kills the flush after earlier writes of the same
        // transaction — proving @Transactional rolls back every change, not only the failing insert.
        String oversized = "x".repeat(1001);

        assertThatThrownBy(() -> wordService.updateWord(aliceId, created.id(),
                form("gamma-renamed",
                        new WordValueForm(original.id(), "nghĩa đã sửa", null, null, null,
                                PartOfSpeech.NOUN),
                        new WordValueForm(null, oversized, null, null, null, PartOfSpeech.NOUN))))
                .isInstanceOf(DataAccessException.class); // MySQL "Data too long" (1406), translated

        List<WordDto> after = wordService.listWords(aliceId);
        assertThat(after).hasSize(1);
        assertThat(after.get(0).english()).isEqualTo("gamma"); // rename rolled back
        assertThat(after.get(0).values()).hasSize(1);
        assertThat(after.get(0).values().get(0).id()).isEqualTo(original.id());
        assertThat(after.get(0).values().get(0).vietnamese()).isEqualTo("nghĩa gốc"); // edit rolled back
        assertThat(meaningRows(created.id())).isEqualTo(1); // no partial insert survived
    }

    @Test
    @DisplayName("update/delete of a foreign or missing word is denied and the data survives")
    void foreignOrMissingWord_deniedAndSurvives() {
        WordDto aliceWord = wordService.createWord(aliceId, form("shared", value("của alice")));
        Long missingId = 9_999_999L;

        assertThatThrownBy(() -> wordService.updateWord(bobId, aliceWord.id(),
                form("stolen", value("x")))).isInstanceOf(OwnershipDeniedException.class);
        assertThatThrownBy(() -> wordService.deleteWord(bobId, aliceWord.id()))
                .isInstanceOf(OwnershipDeniedException.class);
        assertThatThrownBy(() -> wordService.updateWord(aliceId, missingId,
                form("ghost", value("x")))).isInstanceOf(OwnershipDeniedException.class);
        assertThatThrownBy(() -> wordService.deleteWord(aliceId, missingId))
                .isInstanceOf(OwnershipDeniedException.class); // missing answers like foreign: no leak

        List<Word> remaining = wordRepository.findByUser_IdOrderByIdAsc(aliceId);
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).getEnglish()).isEqualTo("shared");
        assertThat(wordService.listWords(bobId)).isEmpty();
    }

    private WordForm form(String english, WordValueForm... values) {
        return new WordForm(english, null, List.of(values));
    }

    private WordValueForm value(String vietnamese) {
        return new WordValueForm(null, vietnamese, null, null, "/v/", PartOfSpeech.NOUN);
    }

    private int wordRows(Long userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM words WHERE user_id = ?",
                Integer.class, userId);
    }

    private int meaningRows(Long wordId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM word_values WHERE word_id = ?",
                Integer.class, wordId);
    }
}
