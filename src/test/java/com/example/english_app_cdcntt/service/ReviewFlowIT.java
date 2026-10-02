package com.example.english_app_cdcntt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.english_app_cdcntt.dto.ReviewResultResponse;
import com.example.english_app_cdcntt.entity.User;
import com.example.english_app_cdcntt.entity.Word;
import com.example.english_app_cdcntt.exception.OwnershipDeniedException;
import com.example.english_app_cdcntt.form.ReviewForm;
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
import org.springframework.test.context.ActiveProfiles;

/**
 * Phase 6 DoD — the SRS review flow of §6.3 end to end against the local MySQL 8.0.43 test database
 * {@code lenglish_test}. Requires the {@code TEST_DB_*} environment variables (see
 * HUONG_DAN_TEST_MYSQL.md) and therefore runs only under failsafe ({@code .\mvnw.cmd clean
 * verify}). Not {@code @Transactional}: the dedupe/lock/schedule commit and the foreign-id rollback
 * must be observed as real committed (or really discarded) rows.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(MySqlTestConfiguration.class)
@DisplayName("SRS review flow on local MySQL")
class ReviewFlowIT {

    private static final String RUN_ID = Long.toHexString(System.nanoTime());

    @Autowired private WordService wordService;
    @Autowired private WordRepository wordRepository;
    @Autowired private UserRepository userRepository;

    private Long aliceId;
    private Long bobId;

    @BeforeEach
    void createUsers() {
        aliceId = userRepository.saveAndFlush(User.create("rev-a-" + RUN_ID, "hash-alice")).getId();
        bobId = userRepository.saveAndFlush(User.create("rev-b-" + RUN_ID, "hash-bob")).getId();
    }

    @AfterEach
    void cleanUp() {
        wordRepository.deleteAll(wordRepository.findByUser_IdOrderByIdAsc(aliceId));
        wordRepository.deleteAll(wordRepository.findByUser_IdOrderByIdAsc(bobId));
        userRepository.deleteById(aliceId);
        userRepository.deleteById(bobId);
    }

    @Test
    @DisplayName("review dedupes [w1,w2,w2] → count 2 and persists the +1d schedule")
    void review_persistsScheduleAndDedupes() {
        Word first = createWord(aliceId, "review-a-" + RUN_ID);
        Word second = createWord(aliceId, "review-b-" + RUN_ID);
        Word untouched = createWord(aliceId, "review-c-" + RUN_ID);
        Instant before = Instant.now();

        ReviewResultResponse result = wordService.review(aliceId,
                new ReviewForm(List.of(first.getId(), second.getId(), second.getId())));
        assertThat(result.reviewedCount()).isEqualTo(2);

        Word reFirst = wordRepository.findById(first.getId()).orElseThrow();
        Word reSecond = wordRepository.findById(second.getId()).orElseThrow();
        Word reUntouched = wordRepository.findById(untouched.getId()).orElseThrow();
        assertThat(reFirst.getReviewCount()).isEqualTo(1);
        assertThat(reSecond.getReviewCount()).isEqualTo(1);
        assertThat(reUntouched.getReviewCount()).isZero();
        assertThat(reFirst.getNextReview()).isAfter(before.truncatedTo(ChronoUnit.SECONDS));
        assertThat(reFirst.getNextReview()).isBefore(before.plus(2, ChronoUnit.DAYS));
    }

    @Test
    @DisplayName("one foreign id rolls the whole batch back — no partial schedule survives")
    void review_rollsBackOnForeignId() {
        Word mine = createWord(aliceId, "review-x-" + RUN_ID);
        Word bobs = createWord(bobId, "review-y-" + RUN_ID);
        Instant untouchedSchedule = mine.getNextReview();

        assertThatThrownBy(() -> wordService.review(aliceId,
                new ReviewForm(List.of(mine.getId(), bobs.getId()))))
                .isInstanceOf(OwnershipDeniedException.class);

        Word reMine = wordRepository.findById(mine.getId()).orElseThrow();
        assertThat(reMine.getReviewCount()).isZero();
        // DATETIME(6) làm tròn micro — so biên độ, không so từng nano
        assertThat(reMine.getNextReview()).isCloseTo(untouchedSchedule, within5s());
    }

    @Test
    @DisplayName("repeating reviews climbs the SRS ladder then holds the 30d interval")
    void review_climbsSrsLadderAndHoldsAtTop() {
        Word word = createWord(aliceId, "review-srs-" + RUN_ID);
        Instant now = Instant.now();

        for (int count = 1; count <= 8; count++) {
            wordService.review(aliceId, new ReviewForm(List.of(word.getId())));
            Word re = wordRepository.findById(word.getId()).orElseThrow();
            assertThat(re.getReviewCount()).isEqualTo(count);
            if (count <= 6) {
                assertThat(re.getNextReview()).isAfter(now);
            }
        }
        Word finalWord = wordRepository.findById(word.getId()).orElseThrow();
        assertThat(finalWord.getNextReview())
                .isCloseTo(now.plus(30, ChronoUnit.DAYS), within5s());
    }

    private Word createWord(Long userId, String english) {
        return wordRepository.saveAndFlush(Word.create(
                userRepository.getReferenceById(userId), english, Instant.now()));
    }

    private static org.assertj.core.data.TemporalUnitOffset within5s() {
        return org.assertj.core.api.Assertions.within(5, ChronoUnit.SECONDS);
    }
}
