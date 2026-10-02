package com.example.english_app_cdcntt;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.english_app_cdcntt.enums.Level;
import com.example.english_app_cdcntt.enums.PartOfSpeech;
import com.example.english_app_cdcntt.service.AiClient;
import com.example.english_app_cdcntt.support.MySqlTestConfiguration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * §11 Phase 7 — HTTP end-to-end happy path: a real JWT crossing every HTTP layer (security
 * filter chain, controller, Bean Validation, service, MySQL) over RANDOM_PORT. AiClient is a
 * stub, so generation and grading never leave localhost. Requires MySQL per
 * HUONG_DAN_TEST_MYSQL.md — fails fast otherwise via MySqlTestConfiguration.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureRestTestClient
@Import(MySqlTestConfiguration.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApiEndToEndIT {

    @Autowired
    private RestTestClient rest;

    @Autowired
    private JdbcTemplate jdbc;

    /** Jackson 3 mapper (Boot 4 auto-configures tools.jackson, not com.fasterxml). */
    private static final ObjectMapper JSON = new ObjectMapper();

    @MockitoBean
    private AiClient aiClient;

    private static String username;
    private static String password;
    private static String accessToken;
    private static String refreshToken;
    private static long wordId1;
    private static long wordId2;
    private static long phraseId;

    @BeforeAll
    static void init() {
        username = "e2e_user_" + Long.toUnsignedString(System.nanoTime());
        password = "Passw0rd!123";
    }

    @AfterAll
    static void cleanUp(@Autowired JdbcTemplate jdbc) {
        jdbc.update("""
                DELETE ge FROM grammar_errors ge
                JOIN phrases p ON p.id = ge.phrase_id
                JOIN users u ON u.id = p.user_id WHERE u.username = ?""", username);
        jdbc.update("DELETE FROM phrases WHERE user_id = (SELECT id FROM users WHERE username = ?)", username);
        jdbc.update("DELETE FROM words WHERE user_id = (SELECT id FROM users WHERE username = ?)", username);
        jdbc.update("DELETE FROM refresh_tokens WHERE user_id = (SELECT id FROM users WHERE username = ?)", username);
        jdbc.update("DELETE FROM users WHERE username = ?", username);
    }

    private String bearer() {
        return "Bearer " + accessToken;
    }

    @Test
    @Order(1)
    @DisplayName("register creates the account (201)")
    void register_createsAccount() {
        rest.post().uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", username, "password", password))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.message").isEqualTo("Tạo tài khoản thành công");
    }

    @Test
    @Order(2)
    @DisplayName("login with a wrong password is 401")
    void login_wrongPassword_answers401() {
        rest.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", username, "password", "Wrong!123"))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @Order(3)
    @DisplayName("login returns a real JWT pair")
    void login_returnsJwtPair() throws Exception {
        String body = rest.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", username, "password", password))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).returnResult().getResponseBody();
        JsonNode json = JSON.readTree(body);
        accessToken = json.path("accessToken").asText();
        refreshToken = json.path("refreshToken").asText();
        assertThat(accessToken).isNotBlank();
        assertThat(refreshToken).isNotBlank();
    }

    @Test
    @Order(4)
    @DisplayName("words endpoint requires authentication")
    void wordsRequireAuthentication() {
        rest.get().uri("/api/words").exchange().expectStatus().isUnauthorized();
    }

    @Test
    @Order(5)
    @DisplayName("generate returns the stubbed AI meaning set without ids")
    void generate_returnsMeaningSet() {
        org.mockito.Mockito.when(aiClient.generateWordMeaning("serendipity"))
                .thenReturn(new AiClient.GeneratedMeaning(true, Level.B2, List.of(
                        new AiClient.MeaningItem("sự tình cờ may mắn", "A serendipity brought us together.",
                                "Một sự tình cờ may mắn đã đưa chúng tôi đến với nhau.",
                                "/ˌser.ənˈdɪp.ə.ti/", PartOfSpeech.NOUN))));
        rest.get().uri("/api/words/generate?english=serendipity")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.english").isEqualTo("serendipity")
                .jsonPath("$.id").doesNotExist()
                .jsonPath("$.values[0].vietnamese").isEqualTo("sự tình cờ may mắn");
    }

    @Test
    @Order(6)
    @DisplayName("create two words (201, real ids)")
    void create_words() throws Exception {
        String body1 = rest.post().uri("/api/words")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("english", "serendipity", "level", "B2",
                        "values", List.of(Map.of("vietnamese", "sự tình cờ may mắn",
                                "example", "A serendipity brought us together.",
                                "exampleTranslation", "Một sự tình cờ may mắn đã đưa chúng tôi đến với nhau.",
                                "pronunciation", "/ˌser.ənˈdɪp.ə.ti/",
                                "partOfSpeech", "NOUN"))))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(String.class).returnResult().getResponseBody();
        wordId1 = JSON.readTree(body1).path("data").path("id").asLong();
        assertThat(wordId1).isPositive();

        String body2 = rest.post().uri("/api/words")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("english", "ephemeral", "level", "B2",
                        "values", List.of(Map.of("vietnamese", "ngắn ngủi, phù du",
                                "example", "Fame is ephemeral.",
                                "exampleTranslation", "Danh tiếng là phù du.",
                                "pronunciation", "/ɪˈfem.ər.əl/",
                                "partOfSpeech", "ADJECTIVE"))))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(String.class).returnResult().getResponseBody();
        wordId2 = JSON.readTree(body2).path("data").path("id").asLong();
        assertThat(wordId2).isPositive().isNotEqualTo(wordId1);
    }

    @Test
    @Order(7)
    @DisplayName("list + due-count see both fresh words")
    void list_and_dueCount() {
        rest.get().uri("/api/words")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(2)
                .jsonPath("$[0].id").isEqualTo((int) wordId1)
                .jsonPath("$[1].english").isEqualTo("ephemeral");
        rest.get().uri("/api/words/due-count")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.dueCount").isEqualTo(2);
    }

    @Test
    @Order(8)
    @DisplayName("update word 1 renames the vietnamese meaning")
    void update_word1() {
        rest.put().uri("/api/words/" + wordId1)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("english", "serendipity", "level", "B2",
                        "values", List.of(Map.of("vietnamese", "sự tình cờ may mắn (đã sửa)",
                                "example", "A serendipity brought us together.",
                                "exampleTranslation", "Một sự tình cờ may mắn đã đưa chúng tôi đến với nhau.",
                                "pronunciation", "/ˌser.ənˈdɪp.ə.ti/",
                                "partOfSpeech", "NOUN"))))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.id").isEqualTo((int) wordId1)
                .jsonPath("$.data.values[0].vietnamese").isEqualTo("sự tình cờ may mắn (đã sửa)");
    }

    @Test
    @Order(9)
    @DisplayName("review dedupes the batch and schedules both words")
    void review_words() {
        rest.post().uri("/api/words/review")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("wordIds", List.of(wordId1, wordId2, wordId2)))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.reviewedCount").isEqualTo(2);
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM words WHERE user_id = (SELECT id FROM users WHERE username = ?) AND next_review IS NOT NULL",
                Long.class, username);
        assertThat(count).isEqualTo(2L);
        Long due = jdbc.queryForObject(
                "SELECT COUNT(*) FROM words WHERE user_id = (SELECT id FROM users WHERE username = ?) AND next_review <= UTC_TIMESTAMP(6)",
                Long.class, username);
        assertThat(due).isEqualTo(0L);
    }

    @Test
    @Order(10)
    @DisplayName("grade + save a phrase persists corrected text and errors")
    void grade_and_save_phrase() throws Exception {
        org.mockito.Mockito.when(aiClient.gradePhrase("I has went to school yesterday."))
                .thenReturn(new AiClient.GradingResult(4, "I went to school yesterday.",
                        List.of(new AiClient.GradingError("I has went", "I went",
                                "Past simple needs 'went', not 'has went'."))));
        String body = rest.post().uri("/api/phrases")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("text", "I has went to school yesterday."))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(String.class).returnResult().getResponseBody();
        JsonNode json = JSON.readTree(body);
        phraseId = json.path("data").path("id").asLong();
        assertThat(phraseId).isPositive();
        assertThat(json.path("data").path("correctedText").asText()).contains("I went");
        assertThat(json.path("data").path("score").asInt()).isEqualTo(4);
    }

    @Test
    @Order(11)
    @DisplayName("phrase list returns the graded paragraph with its errors")
    void list_phrases_and_errors() {
        rest.get().uri("/api/phrases")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].id").isEqualTo((int) phraseId)
                .jsonPath("$[0].errors.length()").isEqualTo(1)
                .jsonPath("$[0].errors[0].incorrect").isEqualTo("I has went")
                .jsonPath("$[0].errors[0].correction").isEqualTo("I went")
                .jsonPath("$[0].errors[0].explanation").isEqualTo("Past simple needs 'went', not 'has went'.");
    }

    @Test
    @Order(12)
    @DisplayName("delete the phrase")
    void delete_phrase() {
        rest.delete().uri("/api/phrases/" + phraseId)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.message").isEqualTo("Xóa đoạn văn thành công");
    }

    @Test
    @Order(13)
    @DisplayName("logout deletes the refresh token row")
    void logout_revokesRefreshToken() {
        rest.post().uri("/api/auth/logout")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("refreshToken", refreshToken))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.message").isEqualTo("Đăng xuất thành công");
        // §5.2:237 — logout hard-deletes the row (no revoked column in refresh_tokens).
        Integer remaining = jdbc.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens WHERE token = ?", Integer.class, refreshToken);
        assertThat(remaining).isZero();
    }

    @Test
    @Order(14)
    @DisplayName("refresh with a deleted token is rejected")
    void refresh_withRevokedToken_rejected() {
        rest.post().uri("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("refreshToken", refreshToken))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @Order(15)
    @DisplayName("DB end state: both words reviewed once, phrase deleted")
    void cleanup_removedAllRows() {
        Long words = jdbc.queryForObject(
                "SELECT COUNT(*) FROM words WHERE user_id = (SELECT id FROM users WHERE username = ?)",
                Long.class, username);
        Long reviewed = jdbc.queryForObject(
                "SELECT COUNT(*) FROM words WHERE user_id = (SELECT id FROM users WHERE username = ?)"
                        + " AND review_count = 1 AND next_review IS NOT NULL",
                Long.class, username);
        Long phrases = jdbc.queryForObject(
                "SELECT COUNT(*) FROM phrases WHERE user_id = (SELECT id FROM users WHERE username = ?)",
                Long.class, username);
        assertThat(words).isEqualTo(2L);
        assertThat(reviewed).isEqualTo(2L);
        assertThat(phrases).isZero();
    }
}
