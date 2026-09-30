package com.example.english_app_cdcntt.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.english_app_cdcntt.enums.Level;
import com.example.english_app_cdcntt.enums.PartOfSpeech;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Phase 1.3 — pure domain rules of the entities: static factories, null/boundary validation,
 * bidirectional collection sync and id-only equality. No Spring context and no database; runs
 * under surefire ({@code mvnw test}) in any environment.
 */
class EntityBusinessRulesTest {

    private static final Instant NOW = Instant.parse("2026-01-15T08:00:00Z");

    private static User user() {
        return User.create("alice", "$2a$10$hashed-secret-value");
    }

    private static <T> T withId(T entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    @Nested
    @DisplayName("User")
    class UserRules {

        @Test
        void createStoresUsernameAndHash() {
            User created = User.create("alice", "hash");
            assertThat(created.getUsername()).isEqualTo("alice");
            assertThat(created.getPassword()).isEqualTo("hash");
            assertThat(created.getId()).isNull();
        }

        @Test
        void createRejectsNullFields() {
            assertThatThrownBy(() -> User.create(null, "hash"))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("username");
            assertThatThrownBy(() -> User.create("alice", null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("password");
        }

        @Test
        void changePasswordReplacesHash() {
            User created = user();
            created.changePassword("new-hash");
            assertThat(created.getPassword()).isEqualTo("new-hash");
            assertThatThrownBy(() -> created.changePassword(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("newPassword");
        }

        @Test
        void toStringOmitsPasswordHash() {
            assertThat(user().toString()).doesNotContain("hashed-secret-value");
        }
    }

    @Nested
    @DisplayName("Word + WordValue")
    class WordRules {

        @Test
        void createStartsAtLevelNullAndCountZero() {
            Word word = Word.create(user(), "hello", NOW);
            assertThat(word.getLevel()).isNull();
            assertThat(word.getReviewCount()).isZero();
            assertThat(word.getNextReview()).isEqualTo(NOW);
            assertThat(word.getWordValues()).isEmpty();
        }

        @Test
        void createRejectsNullFields() {
            assertThatThrownBy(() -> Word.create(null, "hello", NOW))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("user");
            assertThatThrownBy(() -> Word.create(user(), null, NOW))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("english");
            assertThatThrownBy(() -> Word.create(user(), "hello", null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("nextReview");
        }

        @Test
        void addValueKeepsBothSidesInSync() {
            Word word = Word.create(user(), "hello", NOW);
            WordValue value = WordValue.create("xin chao", null, null, null, PartOfSpeech.INTERJECTION);
            word.addValue(value);
            assertThat(word.getWordValues()).containsExactly(value);
            assertThat(value.getWord()).isSameAs(word);
        }

        @Test
        void removeValueDetachesBothSides() {
            Word word = Word.create(user(), "hello", NOW);
            WordValue value = WordValue.create("xin chao", null, null, null, null);
            word.addValue(value);
            word.removeValue(value);
            assertThat(word.getWordValues()).isEmpty();
            assertThat(value.getWord()).isNull();
        }

        @Test
        void valueViewIsUnmodifiable() {
            Word word = Word.create(user(), "hello", NOW);
            assertThatThrownBy(() -> word.getWordValues().add(WordValue.create("x", null, null, null, null)))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void scheduleReviewUpdatesStateAndValidates() {
            Word word = Word.create(user(), "hello", NOW);
            Instant later = NOW.plusSeconds(86_400);
            word.scheduleReview(3, later);
            assertThat(word.getReviewCount()).isEqualTo(3);
            assertThat(word.getNextReview()).isEqualTo(later);

            assertThatThrownBy(() -> word.scheduleReview(-1, later))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("reviewCount must not be negative");
            assertThatThrownBy(() -> word.scheduleReview(1, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("nextReview");
        }

        @Test
        void renameValidatesNullButUpdateLevelAllowsIt() {
            Word word = Word.create(user(), "hello", NOW);
            word.rename("Hello");
            word.updateLevel(Level.A1);
            assertThat(word.getEnglish()).isEqualTo("Hello");
            assertThat(word.getLevel()).isEqualTo(Level.A1);
            assertThatThrownBy(() -> word.rename(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("english");

            // level is nullable until determined (Word.java:59) — updateLevel(null) is a legal reset.
            word.updateLevel(null);
            assertThat(word.getLevel()).isNull();
        }

        @Test
        void valueFactoryRequiresVietnameseOnly() {
            WordValue value = WordValue.create("xin chao", null, null, null, null);
            assertThat(value.getExample()).isNull();
            assertThatThrownBy(() -> WordValue.create(null, null, null, null, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("vietnamese");
        }
    }

    @Nested
    @DisplayName("WordCache + WordCacheValue")
    class WordCacheRules {

        @Test
        void createRejectsNullFields() {
            assertThatThrownBy(() -> WordCache.create(null, Level.A1))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("english");
            assertThatThrownBy(() -> WordCache.create("hello", null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("level");
        }

        @Test
        void addRemoveValueKeepsBothSidesInSync() {
            WordCache cache = WordCache.create("hello", Level.A1);
            WordCacheValue value =
                    WordCacheValue.create("xin chao", "Hello!", "Xin chao!", "/həˈloʊ/", PartOfSpeech.INTERJECTION);
            cache.addValue(value);
            assertThat(cache.getWordCacheValues()).containsExactly(value);
            assertThat(value.getWordCache()).isSameAs(cache);

            cache.removeValue(value);
            assertThat(cache.getWordCacheValues()).isEmpty();
            assertThat(value.getWordCache()).isNull();
        }

        @Test
        void updateLevelReplacesLevel() {
            WordCache cache = WordCache.create("hello", Level.A1);
            cache.updateLevel(Level.C1);
            assertThat(cache.getLevel()).isEqualTo(Level.C1);
            assertThatThrownBy(() -> cache.updateLevel(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("level");
        }

        @Test
        void valueFactoryRequiresEveryField() {
            assertThatThrownBy(() -> WordCacheValue.create(null, "e", "t", "p", PartOfSpeech.NOUN))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("vietnamese");
            assertThatThrownBy(() -> WordCacheValue.create("v", null, "t", "p", PartOfSpeech.NOUN))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("example");
            assertThatThrownBy(() -> WordCacheValue.create("v", "e", null, "p", PartOfSpeech.NOUN))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("exampleTranslation");
            assertThatThrownBy(() -> WordCacheValue.create("v", "e", "t", null, PartOfSpeech.NOUN))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("pronunciation");
            assertThatThrownBy(() -> WordCacheValue.create("v", "e", "t", "p", null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("partOfSpeech");
        }
    }

    @Nested
    @DisplayName("Phrase + GrammarError")
    class PhraseRules {

        @Test
        void createAcceptsBoundaryScores() {
            assertThat(Phrase.create(user(), "i goes", "I go", 0).getScore()).isZero();
            assertThat(Phrase.create(user(), "i goes", "I go", 10).getScore()).isEqualTo(10);
        }

        @Test
        void createRejectsScoreOutsideZeroToTen() {
            assertThatThrownBy(() -> Phrase.create(user(), "i goes", "I go", -1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("score must be between 0 and 10");
            assertThatThrownBy(() -> Phrase.create(user(), "i goes", "I go", 11))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("score must be between 0 and 10");
        }

        @Test
        void createRejectsNullFields() {
            assertThatThrownBy(() -> Phrase.create(null, "t", "c", 5))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("user");
            assertThatThrownBy(() -> Phrase.create(user(), null, "c", 5))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("text");
            assertThatThrownBy(() -> Phrase.create(user(), "t", null, 5))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("correctedText");
        }

        @Test
        void updateResultAppliesSameValidation() {
            Phrase phrase = Phrase.create(user(), "i goes", "I go", 5);
            phrase.updateResult("I go home.", 7);
            assertThat(phrase.getCorrectedText()).isEqualTo("I go home.");
            assertThat(phrase.getScore()).isEqualTo(7);

            assertThatThrownBy(() -> phrase.updateResult("x", 11))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("score must be between 0 and 10");
            assertThat(phrase.getScore()).isEqualTo(7);
        }

        @Test
        void addRemoveErrorKeepsBothSidesInSync() {
            Phrase phrase = Phrase.create(user(), "i goes", "I go", 5);
            GrammarError error = GrammarError.create("goes", "go", "subject-verb agreement");
            phrase.addError(error);
            assertThat(phrase.getGrammarErrors()).containsExactly(error);
            assertThat(error.getPhrase()).isSameAs(phrase);

            phrase.removeError(error);
            assertThat(phrase.getGrammarErrors()).isEmpty();
            assertThat(error.getPhrase()).isNull();
        }

        @Test
        void errorViewIsUnmodifiable() {
            Phrase phrase = Phrase.create(user(), "i goes", "I go", 5);
            assertThatThrownBy(() -> phrase.getGrammarErrors().add(GrammarError.create("i", "I", "cap")))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void errorFactoryRequiresEveryField() {
            assertThatThrownBy(() -> GrammarError.create(null, "c", "e"))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("incorrect");
            assertThatThrownBy(() -> GrammarError.create("i", null, "e"))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("correction");
            assertThatThrownBy(() -> GrammarError.create("i", "c", null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("explanation");
        }
    }

    @Nested
    @DisplayName("RefreshToken")
    class RefreshTokenRules {

        @Test
        void createRejectsNullFields() {
            assertThatThrownBy(() -> RefreshToken.create(null, "tok", NOW))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("user");
            assertThatThrownBy(() -> RefreshToken.create(user(), null, NOW))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("token");
            assertThatThrownBy(() -> RefreshToken.create(user(), "tok", null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("expiryDate");
        }

        @Test
        void isExpiredTreatsExactExpiryAsExpired() {
            Instant expiry = NOW.plusSeconds(60);
            RefreshToken token = RefreshToken.create(user(), "tok", expiry);
            assertThat(token.isExpired(NOW)).isFalse();
            assertThat(token.isExpired(expiry.minusMillis(1))).isFalse();
            assertThat(token.isExpired(expiry)).isTrue();
            assertThat(token.isExpired(expiry.plusSeconds(1))).isTrue();
            assertThatThrownBy(() -> token.isExpired(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("now");
        }

        @Test
        void toStringOmitsTokenValue() {
            RefreshToken token = RefreshToken.create(user(), "super-secret-token", NOW);
            assertThat(token.toString()).doesNotContain("super-secret-token");
        }
    }

    @Nested
    @DisplayName("Identity equality")
    class IdentityEquality {

        @Test
        void equalsAndHashCodeUseIdOnly() {
            User sameIdA = withId(User.create("alice", "h1"), 42L);
            User sameIdB = withId(User.create("bob", "h2"), 42L);
            User otherId = withId(User.create("alice", "h1"), 43L);

            assertThat(sameIdA).isEqualTo(sameIdB).hasSameHashCodeAs(sameIdB);
            assertThat(sameIdA).isNotEqualTo(otherId);
        }

        @Test
        void unsavedEntitiesWithNullIdCompareEqual() {
            // Documented consequence of id-only equality: two unsaved rows compare equal.
            assertThat(Word.create(user(), "hello", NOW)).isEqualTo(Word.create(user(), "world", NOW));
        }
    }
}
