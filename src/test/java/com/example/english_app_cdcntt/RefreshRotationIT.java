package com.example.english_app_cdcntt;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.english_app_cdcntt.service.AiClient;
import com.example.english_app_cdcntt.support.MySqlTestConfiguration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * §11 Phase 7 — trả nợ DoD Phase 2 (§5.2:243): xoay vòng refresh token phải chống tái sử dụng
 * và chống đua. Về bản chất transaction của {@code RefreshTokenTxService.attemptRotation}
 * (PESSIMISTIC_WRITE trên đúng dòng token được trình): người thắng xoay rồi xoá dòng cũ, người
 * thua đọc lại thấy dòng đã mất → 401. Yêu cầu MySQL per HUONG_DAN_TEST_MYSQL.md — fails fast
 * otherwise via MySqlTestConfiguration.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureRestTestClient
@Import(MySqlTestConfiguration.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RefreshRotationIT {

    @Autowired
    private RestTestClient rest;

    @Autowired
    private JdbcTemplate jdbc;

    /** Jackson 3 mapper (Boot 4 auto-configures tools.jackson, not com.fasterxml). */
    private static final ObjectMapper JSON = new ObjectMapper();

    @LocalServerPort
    private int port;

    @MockitoBean
    private AiClient aiClient;

    private static String username;
    private static String password;

    /** State carried across the ordered story: login → rotate → reuse → concurrent race. */
    private static String firstAccessToken;
    private static String firstRefreshToken;
    private static String secondRefreshToken;

    private final HttpClient plain = HttpClient.newHttpClient();

    @BeforeAll
    static void init() {
        username = "rot_user_" + Long.toUnsignedString(System.nanoTime());
        password = "Passw0rd!123";
    }

    @AfterAll
    static void cleanUp(@Autowired JdbcTemplate jdbc) {
        jdbc.update("DELETE FROM refresh_tokens WHERE user_id = (SELECT id FROM users WHERE username = ?)", username);
        jdbc.update("DELETE FROM users WHERE username = ?", username);
    }

    private void login() throws Exception {
        String body = rest.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", username, "password", password))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).returnResult().getResponseBody();
        JsonNode json = JSON.readTree(body);
        firstAccessToken = json.path("accessToken").asText();
        firstRefreshToken = json.path("refreshToken").asText();
    }

    private int refreshPlain(String refreshToken) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/auth/refresh"))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(
                        JSON.writeValueAsString(Map.of("refreshToken", refreshToken)),
                        StandardCharsets.UTF_8))
                .build();
        return plain.send(request, HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    @Test
    @Order(1)
    @DisplayName("registration + first login seed the rotation story")
    void seed() throws Exception {
        rest.post().uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", username, "password", password))
                .exchange()
                .expectStatus().isCreated();
        login();
        assertThat(firstRefreshToken).isNotBlank();
        Integer rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens rt JOIN users u ON u.id = rt.user_id "
                        + "WHERE u.username = ?", Integer.class, username);
        assertThat(rows).isEqualTo(1);
    }

    @Test
    @Order(2)
    @DisplayName("§5.2:243 — two logins in the same second still mint distinct token pairs")
    void logins_sameSecond_newTokens() throws Exception {
        login();
        String access1 = firstAccessToken;
        String refresh1 = firstRefreshToken;
        login(); // issued in the same wall-clock second, different random jti
        assertThat(firstAccessToken).isNotEqualTo(access1);
        assertThat(firstRefreshToken).isNotEqualTo(refresh1);
    }

    @Test
    @Order(3)
    @DisplayName("§5.2:238–240 — refresh rotates: old row deleted, new pair answers")
    void refresh_rotates() throws Exception {
        login();
        String oldRefresh = firstRefreshToken;
        String oldAccess = firstAccessToken;

        String body = rest.post().uri("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("refreshToken", oldRefresh))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).returnResult().getResponseBody();
        JsonNode json = JSON.readTree(body);
        String newAccess = json.path("accessToken").asText();
        String newRefresh = json.path("refreshToken").asText();

        // Same-second issuance must still change every token (random jti per JwtService).
        assertThat(newAccess).isNotEqualTo(oldAccess);
        assertThat(newRefresh).isNotEqualTo(oldRefresh);

        Integer oldRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens WHERE token = ?", Integer.class, oldRefresh);
        Integer newRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens WHERE token = ?", Integer.class, newRefresh);
        assertThat(oldRows).isZero();
        assertThat(newRows).isEqualTo(1);

        // The rotated access token must already authorize protected routes.
        rest.get().uri("/api/words")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + newAccess)
                .exchange()
                .expectStatus().isOk();

        secondRefreshToken = newRefresh;
    }

    @Test
    @Order(4)
    @DisplayName("§5.2:243 — reusing the rotated-away refresh token is 401")
    void reuse_rotatedToken_rejected() throws Exception {
        // One-shot use of the current token consumes it…
        int status = refreshPlain(secondRefreshToken);
        assertThat(status).isEqualTo(200);

        // Now replay the ALREADY-CONSUMED token — the row is gone, commit happened first → 401.
        assertThat(refreshPlain(secondRefreshToken)).isEqualTo(401);

        // Recovery: rotation is net-zero and the rejected replay added nothing.
        Integer rowsBefore = jdbc.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens rt JOIN users u ON u.id = rt.user_id "
                        + "WHERE u.username = ?", Integer.class, username);
        assertThat(refreshPlain(secondRefreshToken)).isEqualTo(401);
        Integer rowsAfter = jdbc.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens rt JOIN users u ON u.id = rt.user_id "
                        + "WHERE u.username = ?", Integer.class, username);
        assertThat(rowsAfter).isEqualTo(rowsBefore);
    }

    @Test
    @Order(5)
    @DisplayName("§5.2:243 — two concurrent refreshes of one token: exactly one wins")
    void concurrentRefresh_exactlyOneWinner() throws Exception {
        login();
        String shared = firstRefreshToken;
        Integer rowsBefore = jdbc.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens rt JOIN users u ON u.id = rt.user_id "
                        + "WHERE u.username = ?", Integer.class, username);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        Future<Integer> a = pool.submit(() -> {
            ready.countDown();
            go.await();
            return refreshPlain(shared);
        });
        Future<Integer> b = pool.submit(() -> {
            ready.countDown();
            go.await();
            return refreshPlain(shared);
        });
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        go.countDown();

        int statusA = a.get(15, TimeUnit.SECONDS);
        int statusB = b.get(15, TimeUnit.SECONDS);
        pool.shutdownNow();

        // PESSIMISTIC_WRITE serialises the race: the winner rotates, the loser reads the
        // deleted row and answers 401 after the winner's commit — never two winners.
        assertThat(java.util.Set.of(statusA, statusB)).containsExactlyInAnyOrder(200, 401);

        // Winner rotated the shared token (-1 +1), loser 401'd: net row count unchanged.
        Integer rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens rt JOIN users u ON u.id = rt.user_id "
                        + "WHERE u.username = ?", Integer.class, username);
        assertThat(rows).isEqualTo(rowsBefore);
        Integer sharedRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens WHERE token = ?", Integer.class, shared);
        assertThat(sharedRows).isZero();
    }
}
