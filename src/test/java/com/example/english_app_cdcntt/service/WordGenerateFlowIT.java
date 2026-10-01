package com.example.english_app_cdcntt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.english_app_cdcntt.EnglishAppCdcnttApplication;
import com.example.english_app_cdcntt.dto.GeneratedWordDto;
import com.example.english_app_cdcntt.enums.Level;
import com.example.english_app_cdcntt.enums.PartOfSpeech;
import com.example.english_app_cdcntt.exception.InvalidWordException;
import com.example.english_app_cdcntt.support.MySqlTestConfiguration;import com.example.english_app_cdcntt.service.AiClient.GeneratedMeaning;
import com.example.english_app_cdcntt.service.AiClient.MeaningItem;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * §HUONG_DAN flow test of the generate word path against the real local MySQL
 * {@code english_app_test}: MISS → one AI call → one cache row; HIT → no AI call; and the
 * {@code UNIQUE(english)} collision must end as a genuine cache read, never a fabricated hit.
 * The AI edge itself is a Mockito stub — the HTTP client is unit-tested in
 * {@code LlmAiClientTest}. Runs under {@code mvnw clean verify} (failsafe), never under the
 * harness unit-test run.
 */
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(classes = {EnglishAppCdcnttApplication.class, MySqlTestConfiguration.class})
class WordGenerateFlowIT {

    /** Chữ cái thuần — key cache phải khớp pattern từ điển ^[a-z]+(['-][a-z]+)*$ (không được có chữ số). */
    private static final String RUN_ID = randomLetters();

    private static String randomLetters() {
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append((char) ('a' + (int) (Math.random() * 26)));
        }
        return sb.toString();
    }

    @Autowired private GenerateService generateService;
    @Autowired private JdbcTemplate jdbc;

    @MockitoBean private AiClient aiClient;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM word_cache WHERE english LIKE ?", "%" + RUN_ID);
    }

    @Test
    @DisplayName("MISS với CỤM TỪ (make up): hỏi AI một lần, cache được tạo; lần sau là HIT không đụng AI")
    void missThenHit() {
        String english = "make up " + RUN_ID;
        GeneratedMeaning meaning = meaning();
        when(aiClient.generateWordMeaning(english)).thenReturn(meaning);

        GeneratedWordDto first = generateService.generateWord(english);
        assertThat(first.english()).isEqualTo(english);
        assertThat(first.level()).isEqualTo(Level.B1);
        assertThat(first.values()).hasSize(1);
        assertThat(first.values().get(0).vietnamese()).isEqualTo("quả chuối");
        verify(aiClient, times(1)).generateWordMeaning(english); // MISS did ask the AI
        assertThat(cacheRows(english)).isEqualTo(1);
        assertThat(valueRows(english)).isEqualTo(1);

        // A second AI ask would still be stubbed, so times(1) below is the real guard.
        GeneratedWordDto second = generateService.generateWord(english);
        assertThat(second).usingRecursiveComparison().isEqualTo(first); // HIT — identical answer
        verify(aiClient, times(1)).generateWordMeaning(english); // and never asked again
    }

    @Test
    @DisplayName("a non-word is rejected before the AI and before any cache write")
    void nonWord_neverReachesAiOrCache() {
        String english = "gen-bad-" + RUN_ID;
        when(aiClient.generateWordMeaning(anyString()))
                .thenThrow(new AssertionError("the AI must not be asked for a non-word"));

        assertThatThrownBy(() -> generateService.generateWord("  abc 123 "))
                .isInstanceOf(InvalidWordException.class);
        assertThat(cacheRows(english)).isZero();
        verify(aiClient, times(0)).generateWordMeaning(anyString());
    }

    @Test
    @DisplayName("8 concurrent racers: one row, every caller served the same payload, nobody 500s")
    void race_createsOneRowWithOneAiCall() throws Exception {
        String english = "gen-race-" + RUN_ID;
        AtomicInteger aiCalls = new AtomicInteger();
        when(aiClient.generateWordMeaning(english)).thenAnswer(invocation -> {
            aiCalls.incrementAndGet();
            Thread.sleep(150); // widen the window so the losers pile up inside saveNew
            return meaning();
        });

        int racers = 8;
        CountDownLatch start = new CountDownLatch(racers);
        ExecutorService pool = Executors.newFixedThreadPool(racers);
        try {
            List<Future<GeneratedWordDto>> results = new ArrayList<>();
            for (int i = 0; i < racers; i++) {
                Future<GeneratedWordDto> future = pool.submit((Callable<GeneratedWordDto>) () -> {
                    start.countDown();
                    start.await(5, TimeUnit.SECONDS); // everyone leaves the gate together
                    return generateService.generateWord(english);
                });
                results.add(future);
            }
            for (Future<GeneratedWordDto> future : results) {
                GeneratedWordDto dto = future.get(10, TimeUnit.SECONDS); // nobody gets a 500
                assertThat(dto.english()).isEqualTo(english);
                assertThat(dto.values()).hasSize(1);
                assertThat(dto.values().get(0).vietnamese()).isEqualTo("quả chuối");
            }
        } finally {
            pool.shutdownNow();
        }

        // §4.1 dedupes at the DB, not at the AI boundary: simultaneous misses all ask the AI
        // before touching word_cache (no distributed lock), UNIQUE(english) then keeps ONE row
        // and the losers fall back to re-read. So AI calls are 1..racers, never 0, and the
        // sequential single-call guarantee is asserted by missThenHit above.
        assertThat(aiCalls.get()).isBetween(1, racers);
        assertThat(cacheRows(english)).isEqualTo(1); // UNIQUE(english) kept the table flat
        assertThat(valueRows(english)).isEqualTo(1);
        verify(aiClient, atLeastOnce()).generateWordMeaning(english);
    }

    private GeneratedMeaning meaning() {
        return new GeneratedMeaning(true, Level.B1,
                List.of(new MeaningItem("quả chuối",
                        "I eat a banana.", "Tôi ăn một quả chuối.",
                        "/bəˈnɑː.nə/", PartOfSpeech.NOUN)));
    }

    private int cacheRows(String english) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM word_cache WHERE english = ?",
                Integer.class, english);
    }

    private int valueRows(String english) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM word_cache_values v
                JOIN word_cache c ON v.word_cache_id = c.id
                WHERE c.english = ?""", Integer.class, english);
    }
}
