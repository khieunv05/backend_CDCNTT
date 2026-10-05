package com.example.english_app_cdcntt.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.english_app_cdcntt.entity.GrammarError;
import com.example.english_app_cdcntt.entity.Phrase;
import com.example.english_app_cdcntt.entity.RefreshToken;
import com.example.english_app_cdcntt.entity.User;
import com.example.english_app_cdcntt.entity.Word;
import com.example.english_app_cdcntt.entity.WordCache;
import com.example.english_app_cdcntt.entity.WordCacheValue;
import com.example.english_app_cdcntt.entity.WordValue;
import com.example.english_app_cdcntt.enums.Level;
import com.example.english_app_cdcntt.enums.PartOfSpeech;
import com.example.english_app_cdcntt.support.MySqlTestConfiguration;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 1.3 — repository layer against the local MySQL 8.0.43 test database {@code lenglish_test}
 * with the schema from {@code V1__init_schema.sql}. Requires the {@code TEST_DB_*} environment
 * variables (see HUONG_DAN_TEST_MYSQL.md) and therefore runs only under failsafe
 * ({@code .\mvnw.cmd clean verify}), never in the harness {@code mvnw test}.
 *
 * <p>Every test is {@code @Transactional} and rolls back. Context startup itself proves
 * {@code spring.jpa.hibernate.ddl-auto=validate} accepts all entity mappings against the migrated
 * schema (Phase 1.4 DoD). Tests that violate a unique key do it as their LAST database action,
 * because the constraint failure marks the shared test transaction rollback-only.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Import(MySqlTestConfiguration.class)
@DisplayName("Repository layer on local MySQL")
class RepositoryLayerIT {

    private static final String RUN_ID = Long.toHexString(System.nanoTime());
    private static final Instant NOW = Instant.parse("2026-01-15T08:00:00Z");

    @Autowired private UserRepository userRepository;
    @Autowired private WordRepository wordRepository;
    @Autowired private WordValueRepository wordValueRepository;
    @Autowired private WordCacheRepository wordCacheRepository;
    @Autowired private WordCacheValueRepository wordCacheValueRepository;
    @Autowired private PhraseRepository phraseRepository;
    @Autowired private GrammarErrorRepository grammarErrorRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private JdbcTemplate jdbc;
    @PersistenceContext private EntityManager entityManager;

    private User alice;
    private User bob;

    @BeforeEach
    void createUsers() {
        alice = userRepository.saveAndFlush(User.create("user-a-" + RUN_ID, "hash-alice"));
        bob = userRepository.saveAndFlush(User.create("user-b-" + RUN_ID, "hash-bob"));
    }

    @Test
    void contextValidatesAllEntityMappingsAgainstMigratedSchema() {
        assertThat(entityManager.getEntityManagerFactory().getMetamodel().getEntities())
                .extracting(type -> type.getJavaType().getSimpleName())
                .contains(
                        "User",
                        "Word",
                        "WordValue",
                        "WordCache",
                        "WordCacheValue",
                        "Phrase",
                        "GrammarError",
                        "RefreshToken");
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo("lenglish_test");
    }

    @Test
    void savingWordCascadesToValuesAndFillsAuditFields() {
        Word word = Word.create(alice, "hello-" + RUN_ID, NOW);
        word.addValue(WordValue.create("xin chao", "Hello!", "Xin chao!", "/həˈloʊ/", PartOfSpeech.INTERJECTION));
        word.addValue(WordValue.create("chao", null, null, null, null));

        Word saved = wordRepository.saveAndFlush(word);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getLevel()).isNull();
        assertThat(saved.getReviewCount()).isZero();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(wordValueRepository.findByWord_Id(saved.getId()))
                .extracting(WordValue::getVietnamese)
                .containsExactlyInAnyOrder("xin chao", "chao");
    }

    @Test
    void orphanRemovalAndBulkDeleteRemoveWordValues() {
        Word word = Word.create(alice, "orphan-" + RUN_ID, NOW);
        word.addValue(WordValue.create("keep-me", null, null, null, null));
        word.addValue(WordValue.create("drop-me", null, null, null, null));
        Long wordId = wordRepository.saveAndFlush(word).getId();
        entityManager.clear();

        Word reloaded = wordRepository.findById(wordId).orElseThrow();
        WordValue drop = reloaded.getWordValues().stream()
                .filter(value -> "drop-me".equals(value.getVietnamese()))
                .findFirst()
                .orElseThrow();
        reloaded.removeValue(drop);
        wordRepository.flush();

        assertThat(wordValueRepository.findByWord_Id(wordId))
                .extracting(WordValue::getVietnamese)
                .containsExactly("keep-me");

        int remainingDeleted = wordValueRepository.deleteByWord_Id(wordId);
        assertThat(remainingDeleted).isEqualTo(1);
        assertThat(wordValueRepository.findByWord_Id(wordId)).isEmpty();
    }

    @Test
    void sameEnglishForDifferentUsersIsAllowed() {
        String english = "shared-" + RUN_ID;
        wordRepository.saveAndFlush(Word.create(alice, english, NOW));
        wordRepository.saveAndFlush(Word.create(bob, english, NOW));

        assertThat(wordRepository.existsByUser_IdAndEnglish(alice.getId(), english)).isTrue();
        assertThat(wordRepository.existsByUser_IdAndEnglish(bob.getId(), english)).isTrue();
    }

    @Test
    void wordQueriesAreIsolatedByOwner() {
        String english = "mine-" + RUN_ID;
        Word word = wordRepository.saveAndFlush(Word.create(alice, english, NOW));
        Long bobId = bob.getId();
        Instant horizon = NOW.plusSeconds(3_600);

        assertThat(wordRepository.findOwnedWithValues(word.getId(), alice.getId())).isPresent();
        assertThat(wordRepository.findOwnedForUpdate(word.getId(), alice.getId())).isPresent();
        assertThat(wordRepository.findByIdAndUser_Id(word.getId(), alice.getId())).isPresent();
        assertThat(wordRepository.findByUser_IdAndEnglish(alice.getId(), english)).isPresent();

        assertThat(wordRepository.findOwnedWithValues(word.getId(), bobId)).isEmpty();
        assertThat(wordRepository.findOwnedForUpdate(word.getId(), bobId)).isEmpty();
        assertThat(wordRepository.findByIdAndUser_Id(word.getId(), bobId)).isEmpty();
        assertThat(wordRepository.findByUser_IdAndEnglish(bobId, english)).isEmpty();
        assertThat(wordRepository.existsByUser_IdAndEnglish(bobId, english)).isFalse();
        assertThat(wordRepository.findByUser_IdAndNextReviewLessThanEqual(bobId, horizon)).isEmpty();
        assertThat(wordRepository.countByUser_IdAndNextReviewLessThanEqual(bobId, horizon)).isZero();
    }

    @Test
    void dueQueryBoundaryIsInclusive() {
        Instant cutoff = NOW.plusSeconds(3_600);
        Word past = wordRepository.saveAndFlush(Word.create(alice, "due-past-" + RUN_ID, cutoff.minusSeconds(1)));
        Word exact = wordRepository.saveAndFlush(Word.create(alice, "due-exact-" + RUN_ID, cutoff));
        wordRepository.saveAndFlush(Word.create(alice, "due-future-" + RUN_ID, cutoff.plusSeconds(1)));

        assertThat(wordRepository.findByUser_IdAndNextReviewLessThanEqual(alice.getId(), cutoff))
                .extracting(Word::getId)
                .containsExactlyInAnyOrder(past.getId(), exact.getId());
        assertThat(wordRepository.countByUser_IdAndNextReviewLessThanEqual(alice.getId(), cutoff)).isEqualTo(2);
    }

    @Test
    void duplicateWordEnglishForSameUserViolatesUniqueKey() {
        String english = "dup-" + RUN_ID;
        wordRepository.saveAndFlush(Word.create(alice, english, NOW));

        // uk_words_user_english — last database action of this test
        assertThatThrownBy(() -> wordRepository.saveAndFlush(Word.create(alice, english, NOW.plusSeconds(60))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void usernameLookupIsCaseSensitiveAndUnique() {
        assertThat(userRepository.findByUsername(alice.getUsername())).isPresent();
        // users.username uses utf8mb4_0900_bin, so lookups are binary (case-sensitive)
        assertThat(userRepository.findByUsername(alice.getUsername().toUpperCase())).isEmpty();
        assertThat(userRepository.existsByUsername(alice.getUsername())).isTrue();

        // uk_users_username — last database action of this test
        assertThatThrownBy(() -> userRepository.saveAndFlush(User.create(alice.getUsername(), "other-hash")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refreshTokenIsUniqueAndFindByTokenLoadsUser() {
        String token = "lookup-" + RUN_ID;
        refreshTokenRepository.saveAndFlush(RefreshToken.create(alice, token, NOW.plusSeconds(604_800)));

        RefreshToken found = refreshTokenRepository.findByToken(token).orElseThrow();
        assertThat(found.getUser().getUsername()).isEqualTo(alice.getUsername());
        assertThat(found.isExpired(NOW)).isFalse();
        assertThat(refreshTokenRepository.findByToken("missing-" + RUN_ID)).isEmpty();

        // uk_refresh_tokens_token — last database action of this test
        assertThatThrownBy(() ->
                        refreshTokenRepository.saveAndFlush(RefreshToken.create(bob, token, NOW.plusSeconds(60))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deleteAllExpiredBeforeRemovesOnlyExpiredTokens() {
        refreshTokenRepository.saveAndFlush(RefreshToken.create(alice, "old-" + RUN_ID, NOW.minusSeconds(86_400)));
        refreshTokenRepository.saveAndFlush(RefreshToken.create(alice, "edge-" + RUN_ID, NOW.minusSeconds(1)));
        // §9.1 — the boundary is inclusive: a token expiring exactly at the cutoff is already dead.
        refreshTokenRepository.saveAndFlush(RefreshToken.create(alice, "at-cutoff-" + RUN_ID, NOW));
        RefreshToken alive =
                refreshTokenRepository.saveAndFlush(RefreshToken.create(alice, "live-" + RUN_ID, NOW.plusSeconds(86_400)));
        Long aliveId = alive.getId();

        int deleted = refreshTokenRepository.deleteAllExpiredBefore(NOW);

        assertThat(deleted).isEqualTo(3);
        assertThat(refreshTokenRepository.findByUser_Id(alice.getId()))
                .extracting(RefreshToken::getId)
                .containsExactly(aliveId);
    }

    @Test
    void wordCacheCascadesValuesAndEnglishIsUnique() {
        String english = "cached-" + RUN_ID;
        WordCache cache = WordCache.create(english, Level.A2);
        cache.addValue(WordCacheValue.create("duoc luu dem", "Hello!", "Xin chao!", "/həˈloʊ/", PartOfSpeech.INTERJECTION));
        WordCache saved = wordCacheRepository.saveAndFlush(cache);

        assertThat(wordCacheValueRepository.findByWordCache_Id(saved.getId())).hasSize(1);

        WordCache found = wordCacheRepository.findByEnglish(english).orElseThrow();
        assertThat(found.getWordCacheValues()).hasSize(1);

        // uk_word_cache_english — last database action of this test
        assertThatThrownBy(() -> wordCacheRepository.saveAndFlush(WordCache.create(english, Level.B1)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void phraseCountByUserIsScopedToOwner() {
        phraseRepository.saveAndFlush(Phrase.create(alice, "i goes home", "I go home", 4));
        phraseRepository.saveAndFlush(Phrase.create(alice, "she go home", "She goes home", 5));

        assertThat(phraseRepository.countByUser_Id(alice.getId())).isEqualTo(2);
        assertThat(phraseRepository.countByUser_Id(bob.getId())).isZero();
    }

    @Test
    void phraseCascadesGrammarErrorsAndBulkDeleteWorks() {
        Phrase phrase = Phrase.create(alice, "i goes", "I go", 3);
        phrase.addError(GrammarError.create("i", "I", "capitalize the pronoun"));
        phrase.addError(GrammarError.create("goes", "go", "subject-verb agreement"));
        Phrase saved = phraseRepository.saveAndFlush(phrase);

        assertThat(grammarErrorRepository.countByPhrase_Id(saved.getId())).isEqualTo(2);

        Phrase loaded = phraseRepository
                .findOwnedWithGrammarErrors(saved.getId(), alice.getId())
                .orElseThrow();
        assertThat(loaded.getGrammarErrors()).hasSize(2);
        assertThat(phraseRepository.findOwnedWithGrammarErrors(saved.getId(), bob.getId())).isEmpty();

        int deleted = grammarErrorRepository.deleteByPhrase_Id(saved.getId());
        assertThat(deleted).isEqualTo(2);
        assertThat(grammarErrorRepository.countByPhrase_Id(saved.getId())).isZero();
    }

    @Test
    void auditTimestampsArePopulatedOnInsert() {
        Word word = wordRepository.saveAndFlush(Word.create(alice, "audit-w-" + RUN_ID, NOW));
        Phrase phrase = phraseRepository.saveAndFlush(Phrase.create(alice, "audit", "Audit", 5));
        RefreshToken token =
                refreshTokenRepository.saveAndFlush(RefreshToken.create(alice, "audit-" + RUN_ID, NOW.plusSeconds(60)));
        WordCache cache = wordCacheRepository.saveAndFlush(WordCache.create("audit-c-" + RUN_ID, Level.A1));

        assertThat(word.getCreatedAt()).isNotNull();
        assertThat(word.getUpdatedAt()).isNotNull();
        assertThat(phrase.getCreatedAt()).isNotNull();
        assertThat(phrase.getUpdatedAt()).isNotNull();
        assertThat(token.getCreatedAt()).isNotNull();
        assertThat(cache.getCreatedAt()).isNotNull();
        assertThat(cache.getUpdatedAt()).isNotNull();
    }

    @Test
    void dueListReturnsAllOwnedWordsOrderedByReviewDate() {
        Word late = wordRepository.saveAndFlush(Word.create(alice, "late-" + RUN_ID, NOW.plusSeconds(7_200)));
        Word early = wordRepository.saveAndFlush(Word.create(alice, "early-" + RUN_ID, NOW));
        wordRepository.saveAndFlush(Word.create(bob, "other-user-" + RUN_ID, NOW));

        List<Word> due =
                wordRepository.findByUser_IdAndNextReviewLessThanEqual(alice.getId(), NOW.plusSeconds(86_400));

        assertThat(due).extracting(Word::getId).containsExactlyInAnyOrder(early.getId(), late.getId());
    }
}
