package com.example.english_app_cdcntt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.english_app_cdcntt.dto.GrammarErrorDto;
import com.example.english_app_cdcntt.dto.PhraseDto;
import com.example.english_app_cdcntt.entity.User;
import com.example.english_app_cdcntt.exception.InvalidPhraseException;
import com.example.english_app_cdcntt.exception.OwnershipDeniedException;
import com.example.english_app_cdcntt.form.PhraseForm;
import com.example.english_app_cdcntt.repository.PhraseRepository;
import com.example.english_app_cdcntt.repository.UserRepository;
import com.example.english_app_cdcntt.service.AiClient.GradingResult;
import com.example.english_app_cdcntt.support.MySqlTestConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * §8.1 DoD — the paragraph grading flow against the local MySQL 8.0.43 test database
 * {@code lenglish_test}. The AI adapter is stubbed with deterministic valid/invalid grading
 * results, so this proves the real transaction and persistence layers, not the LLM.
 * Requires the {@code TEST_DB_*} environment variables and therefore runs only under failsafe
 * ({@code .\mvnw.cmd clean verify}), never in the harness {@code mvnw test}.
 *
 * <p>Deliberately NOT {@code @Transactional}: create and delete commit for real, so the tests can
 * observe committed rows. {@link #cleanUp()} removes the rows this class created; usernames are
 * unique per run through {@link #RUN_ID}.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(MySqlTestConfiguration.class)
@DisplayName("Paragraph grading flow on local MySQL")
class PhraseFlowIT {

    private static final String RUN_ID = Long.toHexString(System.nanoTime());
    private static final String PARAGRAPH =
            "I has went to the store yesterday and buyed some bread, which I eated it fast.";

    @Autowired private PhraseService phraseService;
    @MockitoBean private GradingService gradingService;
    @Autowired private PhraseRepository phraseRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbc;

    private Long aliceId;
    private Long bobId;

    @BeforeEach
    void createUsers() {
        aliceId = userRepository.saveAndFlush(User.create("pflow-a-" + RUN_ID, "hash-a")).getId();
        bobId = userRepository.saveAndFlush(User.create("pflow-b-" + RUN_ID, "hash-b")).getId();
    }

    @AfterEach
    void cleanUp() {
        // phrases first (users are FK-referenced); grammar_errors follow the ON DELETE CASCADE.
        phraseRepository.deleteAll(phraseRepository.findByUser_IdOrderByIdAsc(aliceId));
        phraseRepository.deleteAll(phraseRepository.findByUser_IdOrderByIdAsc(bobId));
        userRepository.deleteById(aliceId);
        userRepository.deleteById(bobId);
        Mockito.reset(gradingService);
    }

    @Test
    @DisplayName("create grades, persists phrase + errors atomically; list returns them with errors")
    void createThenList_persistsGradedPhraseWithErrors() {
        when(gradingService.grade(PARAGRAPH))
                .thenReturn(new GradingResult(8, "I went to the store...", List.of(
                        new AiClient.GradingError("has went", "went", "past participle"),
                        new AiClient.GradingError("buyed", "bought", "irregular verb"))));

        PhraseDto created = phraseService.create(aliceId, new PhraseForm(PARAGRAPH));

        assertThat(created.id()).isNotNull();
        assertThat(created.text()).isEqualTo(PARAGRAPH);
        assertThat(created.score()).isEqualTo(8);
        assertThat(created.correctedText()).isEqualTo("I went to the store...");
        assertThat(created.errors()).hasSize(2);
        assertThat(created.errors().get(0).incorrect()).isEqualTo("has went");
        assertThat(created.errors().get(0).correction()).isEqualTo("went");
        assertThat(created.errors().get(1).incorrect()).isEqualTo("buyed");
        assertThat(created.errors().get(1).explanation()).isEqualTo("irregular verb");
        assertThat(phraseRows(created.id())).isEqualTo(2);
        assertThat(phraseRowsFor(aliceId)).isEqualTo(1);

        List<PhraseDto> listed = phraseService.list(aliceId);
        assertThat(listed).hasSize(1);
        assertThat(listed.get(0).id()).isEqualTo(created.id());
        assertThat(listed.get(0).errors()).extracting(GrammarErrorDto::correction)
                .containsExactly("went", "bought");
    }

    @Test
    @DisplayName("invalid paragraph (provider validPhrase=false → adapter throws) stores nothing")
    void create_notAParagraph_storesNothing() {
        when(gradingService.grade("hello world"))
                .thenThrow(new InvalidPhraseException());

        assertThatThrownBy(() -> phraseService.create(aliceId, new PhraseForm("hello world")))
                .isInstanceOf(InvalidPhraseException.class)
                .hasMessage("Đoạn văn gửi lên không hợp lệ");

        assertThat(phraseRowsFor(aliceId)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM grammar_errors", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("delete removes only the owner's phrase; cascade removes the error rows")
    void delete_removesPhraseAndErrorRows_cascade() {
        when(gradingService.grade(anyString()))
                .thenReturn(new GradingResult(7, "fixed", List.of(
                        new AiClient.GradingError("has went", "went", "pp"))));
        PhraseDto created = phraseService.create(aliceId, new PhraseForm(PARAGRAPH));

        assertThatCode(() -> phraseService.delete(bobId, created.id()))
                .isInstanceOf(OwnershipDeniedException.class);
        assertThatCode(() -> phraseService.delete(aliceId, 9_999_999L))
                .isInstanceOf(OwnershipDeniedException.class);
        assertThat(phraseRowsFor(aliceId)).isEqualTo(1); // foreign/missing delete touched nothing

        phraseService.delete(aliceId, created.id());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM phrases WHERE id = ?",
                Integer.class, created.id())).isZero();
        assertThat(phraseRows(created.id())).isZero();
        assertThat(phraseRowsFor(bobId)).isZero();
    }

    private int phraseRows(Long phraseId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM grammar_errors WHERE phrase_id = ?",
                Integer.class, phraseId);
    }

    private int phraseRowsFor(Long userId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM phrases WHERE user_id = ?", Integer.class, userId);
    }
}
