package com.example.english_app_cdcntt;

import com.example.english_app_cdcntt.support.MySqlTestConfiguration;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(MySqlTestConfiguration.class)
class EnglishAppCdcnttApplicationIT {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 2, 3, 4, 5, 123456000);
    private static final List<String> TABLES = List.of("users", "words", "word_values", "word_cache",
            "word_cache_values", "phrases", "grammar_errors", "refresh_tokens");

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    Flyway flyway;

    @Test
    void migration_onDedicatedMySql_shouldHaveV1AndRemainValidOnSecondRun() {
        // Provision an empty schema for the first run; subsequent runs retain Flyway history.
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo("lenglish_test");
        assertThat(jdbc.queryForObject("SELECT VERSION()", String.class)).startsWith("8.0.43");
        assertThat(jdbc.queryForObject("SELECT @@session.time_zone", String.class)).isEqualTo("+00:00");
        assertThat(jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name <> 'flyway_schema_history'
                """, String.class)).containsExactlyInAnyOrderElementsOf(TABLES);
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("1");
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void schema_shouldHaveExpectedStorageTypesCollationsAndIndexes() {
        for (String table : TABLES) {
            assertThat(jdbc.queryForObject("""
                    SELECT engine FROM information_schema.tables
                    WHERE table_schema = DATABASE() AND table_name = ?
                    """, String.class, table)).isEqualTo("InnoDB");
            assertThat(column(table, "id", "data_type")).isEqualTo("bigint");
            assertThat(column(table, "id", "extra")).isEqualTo("auto_increment");
        }
        assertThat(column("users", "username", "collation_name")).isEqualTo("utf8mb4_0900_bin");
        assertThat(column("words", "english", "collation_name")).isEqualTo("utf8mb4_0900_bin");
        assertThat(column("word_cache", "english", "collation_name")).isEqualTo("utf8mb4_0900_bin");
        assertThat(column("refresh_tokens", "token", "collation_name")).isEqualTo("ascii_bin");
        assertThat(column("refresh_tokens", "token", "character_set_name")).isEqualTo("ascii");
        assertLength("users", "username", 50);
        assertLength("users", "password", 60);
        assertLength("refresh_tokens", "token", 2048);
        for (String table : List.of("words", "word_cache")) {
            assertLength(table, "english", 255);
            assertLength(table, "level", 2);
        }
        for (String table : List.of("word_values", "word_cache_values")) {
            assertLength(table, "vietnamese", 1000);
            assertLength(table, "example", 2000);
            assertLength(table, "example_translation", 2000);
            assertLength(table, "pronunciation", 255);
            assertLength(table, "part_of_speech", 20);
        }
        for (String field : List.of("text", "corrected_text")) {
            assertThat(column("phrases", field, "data_type")).isEqualTo("text");
        }
        for (String field : List.of("incorrect", "correction", "explanation")) {
            assertThat(column("grammar_errors", field, "data_type")).isEqualTo("text");
        }
        assertThat(jdbc.queryForList("""
                SELECT CONCAT(table_name, '.', column_name) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND is_nullable = 'YES'
                  AND table_name <> 'flyway_schema_history'
                """, String.class)).containsExactlyInAnyOrder("words.level", "word_values.example",
                "word_values.example_translation", "word_values.pronunciation", "word_values.part_of_speech");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND data_type = 'datetime' AND datetime_precision = 6
                """, Integer.class)).isEqualTo(9);
        assertThat(column("words", "review_count", "column_default")).isEqualTo("0");
        // Compare every index: also catches accidental duplicate FK indexes.
        assertIndexes("users", "PRIMARY:id", "uk_users_username:username");
        assertIndexes("words", "PRIMARY:id", "uk_words_user_english:user_id,english",
                "idx_words_user_next_review:user_id,next_review");
        assertIndexes("word_values", "PRIMARY:id", "idx_word_values_word:word_id");
        assertIndexes("word_cache", "PRIMARY:id", "uk_word_cache_english:english");
        assertIndexes("word_cache_values", "PRIMARY:id", "idx_word_cache_values_cache:word_cache_id");
        assertIndexes("phrases", "PRIMARY:id", "idx_phrases_user:user_id");
        assertIndexes("grammar_errors", "PRIMARY:id", "idx_grammar_errors_phrase:phrase_id");
        assertIndexes("refresh_tokens", "PRIMARY:id", "uk_refresh_tokens_token:token",
                "idx_refresh_tokens_user:user_id", "idx_refresh_tokens_expiry:expiry_date");
        assertThat(jdbc.queryForList("""
                SELECT CONCAT(constraint_name, ':', delete_rule) FROM information_schema.referential_constraints
                WHERE constraint_schema = DATABASE()
                """, String.class)).containsExactlyInAnyOrder("fk_words_user:RESTRICT",
                "fk_word_values_word:CASCADE", "fk_word_cache_values_cache:CASCADE",
                "fk_phrases_user:RESTRICT", "fk_grammar_errors_phrase:CASCADE", "fk_refresh_tokens_user:RESTRICT");
    }

    @Test
    @Transactional
    void uniqueKeys_shouldBeCaseSensitiveAndScopePersonalWordsByUser() {
        user(1, "alice");
        user(2, "Alice");
        assertRejected(() -> user(3, "alice"));
        word(1, 1, "apple");
        word(2, 2, "apple");
        // Canonical lowercase is an application responsibility, not a DB transformation.
        word(3, 1, "Apple");
        assertRejected(() -> word(4, 1, "apple"));
        cache(1, "apple");
        cache(2, "Apple");
        assertRejected(() -> cache(3, "apple"));
        token(1, 1, "Abc");
        token(2, 1, "abc");
        assertRejected(() -> token(3, 2, "Abc"));
    }

    @Test
    @Transactional
    void constraints_shouldRejectInvalidScoresReviewCountsNullsAndOrphans() {
        user(1, "owner");
        word(1, 1, "apple");
        phrase(1, 1, 0);
        phrase(2, 1, 10);
        assertThat(jdbc.queryForObject("SELECT review_count FROM words WHERE id = 1", Integer.class)).isZero();
        assertCheckRejected(() -> jdbc.update("UPDATE words SET review_count = -1 WHERE id = 1"),
                "ck_words_review_count");
        assertCheckRejected(() -> phrase(3, 1, -1), "ck_phrases_score");
        assertCheckRejected(() -> phrase(4, 1, 11), "ck_phrases_score");
        assertRejected(() -> jdbc.update("UPDATE words SET next_review = NULL WHERE id = 1"));
        assertRejected(() -> word(5, 999, "orphan"));
        assertRejected(() -> phrase(5, 999, 5));
        assertRejected(() -> token(5, 999, "orphan"));
        assertRejected(() -> jdbc.update("INSERT INTO word_values (word_id, vietnamese) VALUES (999, 'nghĩa')"));
        assertRejected(() -> cacheValue(1, 999));
        assertRejected(() -> grammarError(1, 999));
        assertRejected(() -> jdbc.update("DELETE FROM users WHERE id = 1"));
    }

    @Test
    @Transactional
    void deletingParents_shouldCascadeOnlyToTheirChildrenAndLeaveCacheIndependent() {
        user(1, "owner");
        word(1, 1, "apple");
        word(2, 1, "pear");
        jdbc.update("INSERT INTO word_values (word_id, vietnamese) VALUES (1, 'táo'), (2, 'lê')");
        cache(1, "apple");
        cache(2, "pear");
        cacheValue(1, 1);
        cacheValue(2, 2);
        phrase(1, 1, 5);
        phrase(2, 1, 6);
        grammarError(1, 1);
        grammarError(2, 2);
        jdbc.update("DELETE FROM words WHERE id = 1");
        assertThat(count("word_values", "word_id", 1)).isZero();
        assertThat(count("word_values", "word_id", 2)).isEqualTo(1);
        assertThat(count("word_cache", "id", 1)).isEqualTo(1);
        jdbc.update("DELETE FROM word_cache WHERE id = 2");
        assertThat(count("word_cache_values", "word_cache_id", 2)).isZero();
        assertThat(count("word_cache_values", "word_cache_id", 1)).isEqualTo(1);
        assertThat(count("words", "id", 2)).isEqualTo(1);
        jdbc.update("DELETE FROM phrases WHERE id = 1");
        assertThat(count("grammar_errors", "phrase_id", 1)).isZero();
        assertThat(count("grammar_errors", "phrase_id", 2)).isEqualTo(1);
    }

    @Test
    @Transactional
    void dataTypes_shouldPreserveUnicodeLongTextAndMicrosecondsAndRejectOverflow() {
        user(1, "người_học_😀");
        word(1, 1, "apple");
        jdbc.update("INSERT INTO word_values (word_id, vietnamese) VALUES (1, ?)", "nghĩa😀".repeat(100));
        phrase(1, 1, 5);
        String text = "á".repeat(5000);
        String correction = "b".repeat(10000);
        jdbc.update("UPDATE phrases SET text = ?, corrected_text = ? WHERE id = 1", text, correction);
        assertThat(jdbc.queryForObject("SELECT text FROM phrases WHERE id = 1", String.class)).isEqualTo(text);
        assertThat(jdbc.queryForObject("SELECT corrected_text FROM phrases WHERE id = 1", String.class)).isEqualTo(correction);
        assertThat(jdbc.queryForObject("SELECT next_review FROM words WHERE id = 1", LocalDateTime.class)).isEqualTo(NOW);
        token(1, 1, "x".repeat(2048));
        assertRejected(() -> token(2, 1, "x".repeat(2049)));
        assertRejected(() -> user(2, "x".repeat(51)));
        assertRejected(() -> word(2, 1, "x".repeat(256)));
    }

    private void user(long id, String username) {
        jdbc.update("INSERT INTO users (id, username, password) VALUES (?, ?, ?)", id, username, "x".repeat(60));
    }

    private void word(long id, long userId, String english) {
        jdbc.update("""
                INSERT INTO words (id, user_id, english, next_review, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, id, userId, english, NOW, NOW, NOW);
    }

    private void cache(long id, String english) {
        jdbc.update("INSERT INTO word_cache (id, english, level, created_at, updated_at) VALUES (?, ?, 'A1', ?, ?)",
                id, english, NOW, NOW);
    }

    private void cacheValue(long id, long cacheId) {
        jdbc.update("""
                INSERT INTO word_cache_values
                (id, word_cache_id, vietnamese, example, example_translation, pronunciation, part_of_speech)
                VALUES (?, ?, 'táo', 'An apple', 'Một quả táo', 'apple', 'NOUN')
                """, id, cacheId);
    }

    private void phrase(long id, long userId, int score) {
        jdbc.update("INSERT INTO phrases (id, user_id, text, corrected_text, score, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, userId, "An example sentence.", "An example sentence.", score, NOW, NOW);
    }

    private void grammarError(long id, long phraseId) {
        jdbc.update("INSERT INTO grammar_errors (id, phrase_id, incorrect, correction, explanation) VALUES (?, ?, 'a', 'an', 'reason')", id, phraseId);
    }

    private void token(long id, long userId, String token) {
        jdbc.update("INSERT INTO refresh_tokens (id, user_id, token, expiry_date, created_at) VALUES (?, ?, ?, ?, ?)",
                id, userId, token, NOW.plusDays(7), NOW);
    }

    // Identifiers below are hardcoded test metadata, never external input.
    private String column(String table, String column, String attribute) {
        return jdbc.queryForObject("SELECT " + attribute + " FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
                String.class, table, column);
    }

    private void assertLength(String table, String column, int length) {
        assertThat(column(table, column, "character_maximum_length")).isEqualTo(Integer.toString(length));
    }

    private void assertIndexes(String table, String... indexes) {
        assertThat(jdbc.queryForList("""
                SELECT CONCAT(index_name, ':', GROUP_CONCAT(column_name ORDER BY seq_in_index))
                FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = ?
                GROUP BY index_name
                """, String.class, table)).containsExactlyInAnyOrder(indexes);
    }

    private int count(String table, String column, long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?", Integer.class, id);
    }

    private void assertRejected(Runnable statement) {
        assertThatThrownBy(statement::run).isInstanceOf(DataIntegrityViolationException.class);
    }

    private void assertCheckRejected(Runnable statement, String constraint) {
        // MySQL reports CHECK violations as HY000/3819, which Spring may leave uncategorized.
        assertThatThrownBy(statement::run)
                .isInstanceOf(DataAccessException.class)
                .rootCause()
                .isInstanceOfSatisfying(SQLException.class, sql -> {
                    assertThat(sql.getSQLState()).isEqualTo("HY000");
                    assertThat(sql.getErrorCode()).isEqualTo(3819);
                    assertThat(sql.getMessage()).contains("'" + constraint + "'");
                });
    }
}
