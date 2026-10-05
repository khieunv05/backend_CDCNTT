package com.example.english_app_cdcntt.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.english_app_cdcntt.config.AiProperties;
import com.example.english_app_cdcntt.config.SecretValue;
import com.example.english_app_cdcntt.enums.Level;
import com.example.english_app_cdcntt.enums.PartOfSpeech;
import com.example.english_app_cdcntt.exception.AiServiceException;
import com.example.english_app_cdcntt.exception.InvalidPhraseException;
import com.example.english_app_cdcntt.service.AiClient;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/**
 * HTTP stub tests for the §8.2 LLM adapter: timeout, 429/5xx, malformed body and every wrong
 * schema variant must collapse into one client-safe {@link AiServiceException} with a single
 * request (no retry), while a good payload is mapped without any implicit coercion.
 */
class LlmAiClientTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private MockRestServiceServer server;
    private LlmAiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        AiProperties properties = new AiProperties(URI.create("https://ai.test"),
                new SecretValue("test-key"), "gpt-test", Duration.ofSeconds(10),
                Duration.ofSeconds(30));
        client = new LlmAiClient(
                builder.baseUrl("https://ai.test").build(), properties, JSON);
    }

    private String envelope(String contentJson) throws Exception {
        var root = JSON.createObjectNode();
        root.putArray("choices").addObject().putObject("message").put("content", contentJson);
        return root.toString();
    }

    private String goodContent() {
        return "{\"validWord\":true,\"level\":\"B1\",\"values\":[{"
                + "\"vietnamese\":\"quả chuối\","
                + "\"example\":\"I eat a banana.\","
                + "\"exampleTranslation\":\"Tôi ăn một quả chuối.\","
                + "\"pronunciation\":\"/bəˈnɑː.nə/\","
                + "\"partOfSpeech\":\"NOUN\"}]}";
    }

    @Test
    @DisplayName("payload hợp lệ → GeneratedMeaning đầy đủ, đúng enum, request chuẩn")
    void mapsGoodPayload() throws Exception {
        server.expect(requestTo("https://ai.test/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.model").value("gpt-test"))
                .andExpect(jsonPath("$.messages[1].content").value("Word to analyze: banana"))
                .andRespond(withSuccess(envelope(goodContent()), MediaType.APPLICATION_JSON));

        AiClient.GeneratedMeaning meaning = client.generateWordMeaning("banana");

        assertThat(meaning.validWord()).isTrue();
        assertThat(meaning.level()).isEqualTo(Level.B1);
        assertThat(meaning.values()).hasSize(1);
        AiClient.MeaningItem item = meaning.values().get(0);
        assertThat(item.vietnamese()).isEqualTo("quả chuối");
        assertThat(item.partOfSpeech()).isEqualTo(PartOfSpeech.NOUN);
        server.verify(); // đúng một request — không retry (§8.2)
    }

    @Test
    @DisplayName("validWord=false → chỉ cần cờ, không bắt buộc các trường còn lại")
    void passesNonWordThrough() throws Exception {
        server.expect(requestTo("https://ai.test/chat/completions"))
                .andRespond(withSuccess(envelope("{\"validWord\":false}"),
                        MediaType.APPLICATION_JSON));

        AiClient.GeneratedMeaning meaning = client.generateWordMeaning("asdfgh");

        assertThat(meaning.validWord()).isFalse();
        assertThat(meaning.level()).isNull();
        assertThat(meaning.values()).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("429 Too Many Requests → 502, không retry")
    void maps429ToServiceException() {
        server.expect(requestTo("https://ai.test/chat/completions"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client.generateWordMeaning("banana"))
                .isInstanceOf(AiServiceException.class);
        server.verify();
    }

    @Test
    @DisplayName("500 → 502")
    void maps500ToServiceException() {
        server.expect(requestTo("https://ai.test/chat/completions"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.generateWordMeaning("banana"))
                .isInstanceOf(AiServiceException.class);
        server.verify();
    }

    @Test
    @DisplayName("timeout mạng → 502")
    void mapsTimeoutToServiceException() {
        server.expect(requestTo("https://ai.test/chat/completions"))
                .andRespond(withException(new SocketTimeoutException("read timed out")));

        assertThatThrownBy(() -> client.generateWordMeaning("banana"))
                .isInstanceOf(AiServiceException.class);
        server.verify();
    }

    @Test
    @DisplayName("body không phải JSON → 502")
    void malformedBodyIsServiceException() {
        server.expect(requestTo("https://ai.test/chat/completions"))
                .andRespond(withSuccess("<html>boom</html>", MediaType.TEXT_HTML));

        assertThatThrownBy(() -> client.generateWordMeaning("banana"))
                .isInstanceOf(AiServiceException.class);
        server.verify();
    }

    @Test
    @DisplayName("thiếu choices[0].message.content → 502")
    void missingContentIsServiceException() throws Exception {
        server.expect(requestTo("https://ai.test/chat/completions"))
                .andRespond(withSuccess("{\"choices\":[{\"message\":{}}]}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.generateWordMeaning("banana"))
                .isInstanceOf(AiServiceException.class);
        server.verify();
    }

    @Test
    @DisplayName("content không parse được JSON → 502")
    void nonJsonContentIsServiceException() throws Exception {
        server.expect(requestTo("https://ai.test/chat/completions"))
                .andRespond(withSuccess(envelope("xin chào đây là văn xuôi"),
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.generateWordMeaning("banana"))
                .isInstanceOf(AiServiceException.class);
        server.verify();
    }

    @Test
    @DisplayName("không ép kiểu ngầm: validWord là chuỗi 'true' → 502")
    void rejectsCoercedValidWord() throws Exception {
        server.expect(requestTo("https://ai.test/chat/completions"))
                .andRespond(withSuccess(envelope("{\"validWord\":\"true\"}"),
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.generateWordMeaning("banana"))
                .isInstanceOf(AiServiceException.class);
        server.verify();
    }

    @Test
    @DisplayName("level ngoài CEFR → 502")
    void rejectsUnknownLevel() throws Exception {
        server.expect(requestTo("https://ai.test/chat/completions"))
                .andRespond(withSuccess(envelope(
                        "{\"validWord\":true,\"level\":\"Z9\",\"values\":[]}"),
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.generateWordMeaning("banana"))
                .isInstanceOf(AiServiceException.class);
        server.verify();
    }

    @Test
    @DisplayName("validWord=true mà 0 nghĩa → 502")
    void rejectsEmptyValues() throws Exception {
        server.expect(requestTo("https://ai.test/chat/completions"))
                .andRespond(withSuccess(envelope(
                        "{\"validWord\":true,\"level\":\"B1\",\"values\":[]}"),
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.generateWordMeaning("banana"))
                .isInstanceOf(AiServiceException.class);
        server.verify();
    }

    @Test
    @DisplayName("4 nghĩa (>3) → 502")
    void rejectsTooManyValues() throws Exception {
        var root = JSON.createObjectNode().put("validWord", true).put("level", "B1");
        var values = root.putArray("values");
        for (int i = 0; i < 4; i++) {
            values.addObject().put("vietnamese", "nghĩa " + i)
                    .put("example", "an example " + i)
                    .put("exampleTranslation", "bản dịch " + i)
                    .put("pronunciation", "/x/")
                    .put("partOfSpeech", "NOUN");
        }
        server.expect(requestTo("https://ai.test/chat/completions"))
                .andRespond(withSuccess(envelope(JSON.writeValueAsString(root)),
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.generateWordMeaning("banana"))
                .isInstanceOf(AiServiceException.class);
        server.verify();
    }

    @Test
    @DisplayName("thiếu trường nghĩa → 502")
    void rejectsIncompleteMeaning() throws Exception {
        server.expect(requestTo("https://ai.test/chat/completions"))
                .andRespond(withSuccess(envelope(
                        "{\"validWord\":true,\"level\":\"B1\",\"values\":[{"
                                + "\"vietnamese\":\"quả chuối\",\"partOfSpeech\":\"NOUN\"}]}"),
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.generateWordMeaning("banana"))
                .isInstanceOf(AiServiceException.class);
        server.verify();
    }

    @Test
    @DisplayName("partOfSpeech ngoài enum → 502")
    void rejectsUnknownPartOfSpeech() throws Exception {
        server.expect(requestTo("https://ai.test/chat/completions"))
                .andRespond(withSuccess(envelope(goodContent()
                        .replace("NOUN", "ANIMAL")), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.generateWordMeaning("banana"))
                .isInstanceOf(AiServiceException.class);
        server.verify();
    }

    @Test
    @DisplayName("vietnamese vượt 1000 ký tự (giới hạn cột) → 502")
    void rejectsOversizedField() throws Exception {
        String oversized = "x".repeat(1001);
        server.expect(requestTo("https://ai.test/chat/completions"))
                .andRespond(withSuccess(envelope(goodContent()
                        .replace("quả chuối", oversized)), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.generateWordMeaning("banana"))
                .isInstanceOf(AiServiceException.class);
        server.verify();
    }

    @Test
    @DisplayName("vietnamese đúng 1000 ký tự vẫn hợp lệ (biên trên cột)")
    void acceptsMaxLengthBoundary() throws Exception {
        String boundary = "x".repeat(1000);
        server.expect(requestTo("https://ai.test/chat/completions"))
                .andRespond(withSuccess(envelope(goodContent()
                        .replace("quả chuối", boundary)), MediaType.APPLICATION_JSON));

        AiClient.GeneratedMeaning meaning = client.generateWordMeaning("banana");
        assertThat(meaning.validWord()).isTrue();
        assertThat(meaning.values().get(0).vietnamese()).hasSize(1000);
        server.verify();
    }

    // ---- gradePhrase (Phase 5, §4.1 rows 13–14) ----

    private AiClient.GradingError error(String incorrect, String correction, String explanation) {
        return new AiClient.GradingError(incorrect, correction, explanation);
    }

    private AiClient.GradingResult grade(String contentJson) throws Exception {
        server.expect(requestTo("https://ai.test/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(envelope(contentJson), MediaType.APPLICATION_JSON));
        return client.gradePhrase("I have went to the store yesterday.");
    }

    private String goodGradingContent() {
        return "{\"validPhrase\":true,\"score\":8,"
                + "\"correctedText\":\"I went to the store yesterday.\","
                + "\"errors\":[{\"incorrect\":\"have went\",\"correction\":\"went\","
                + "\"explanation\":\"Past simple, not present perfect.\"}]}";
    }

    @Test
    @DisplayName("chấm điểm: payload chuẩn → GradingResult đầy đủ")
    void mapsGoodGrading() throws Exception {
        AiClient.GradingResult result = grade(goodGradingContent());
        assertThat(result.score()).isEqualTo(8);
        assertThat(result.correctedText()).isEqualTo("I went to the store yesterday.");
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0)).isEqualTo(
                error("have went", "went", "Past simple, not present perfect."));
        server.verify();
    }

    @Test
    @DisplayName("incorrect/correction rỗng vẫn hợp lệ, explanation là bắt buộc")
    void allowsEmptyIncorrectAndCorrection() throws Exception {
        String content = "{\"validPhrase\":true,\"score\":0,\"correctedText\":\"Hello.\","
                + "\"errors\":[{\"incorrect\":\"\",\"correction\":\"\",\"explanation\":\"ok\"}]}";
        AiClient.GradingResult result = grade(content);
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0).incorrect()).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("validPhrase=false → InvalidPhraseException 400 theo §8.2:355 (cờ ở adapter)")
    void passesNonPhraseThrough() throws Exception {
        assertThatThrownBy(() -> grade("{\"validPhrase\":false}"))
                .isInstanceOf(InvalidPhraseException.class);
        server.verify();
    }

    @Test
    @DisplayName("score 11 và -1 → 502 (không kẹp biên)")
    void rejectsScoreOutOfRange() throws Exception {
        server.expect(requestTo("https://ai.test/chat/completions"))
                .andRespond(withSuccess(envelope("{\"validPhrase\":true,\"score\":11,"
                        + "\"correctedText\":\"x\",\"errors\":[]}"), MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://ai.test/chat/completions"))
                .andRespond(withSuccess(envelope("{\"validPhrase\":true,\"score\":-1,"
                        + "\"correctedText\":\"x\",\"errors\":[]}"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.gradePhrase("Some text."))
                .isInstanceOf(AiServiceException.class);
        assertThatThrownBy(() -> client.gradePhrase("Some text."))
                .isInstanceOf(AiServiceException.class);
        server.verify();
    }

    @Test
    @DisplayName("score là chuỗi \"8\" → 502 (cấm coercion)")
    void rejectsStringScore() throws Exception {
        assertThatThrownBy(() -> grade(goodGradingContent().replace("\"score\":8", "\"score\":\"8\"")))
                .isInstanceOf(AiServiceException.class);
        server.verify();
    }

    @Test
    @DisplayName("thiếu score → 502")
    void rejectsMissingScore() throws Exception {
        assertThatThrownBy(() -> grade(goodGradingContent().replace("\"score\":8,", "")))
                .isInstanceOf(AiServiceException.class);
        server.verify();
    }

    @Test
    @DisplayName("correctedText rỗng → 502")
    void rejectsBlankCorrectedText() throws Exception {
        assertThatThrownBy(() -> grade(goodGradingContent()
                .replace("I went to the store yesterday.", " ")))
                .isInstanceOf(AiServiceException.class);
        server.verify();
    }

    @Test
    @DisplayName("correctedText vượt 10000 ký tự → 502")
    void rejectsOversizedCorrectedText() throws Exception {
        String oversized = "y".repeat(10001);
        assertThatThrownBy(() -> grade(goodGradingContent()
                .replace("I went to the store yesterday.", oversized)))
                .isInstanceOf(AiServiceException.class);
        server.verify();
    }

    @Test
    @DisplayName("explanation rỗng → 502")
    void rejectsBlankExplanation() throws Exception {
        assertThatThrownBy(() -> grade(goodGradingContent()
                .replace("Past simple, not present perfect.", " ")))
                .isInstanceOf(AiServiceException.class);
        server.verify();
    }

    @Test
    @DisplayName("101 lỗi → 502")
    void rejectsTooManyErrors() throws Exception {
        var root = JSON.createObjectNode().put("validPhrase", true).put("score", 5)
                .put("correctedText", "ok");
        var errors = root.putArray("errors");
        for (int i = 0; i < 101; i++) {
            errors.addObject().put("incorrect", "a").put("correction", "b")
                    .put("explanation", "c" + i);
        }
        assertThatThrownBy(() -> grade(root.toString()))
                .isInstanceOf(AiServiceException.class);
        server.verify();
    }

    @Test
    @DisplayName("validPhrase là chuỗi \"true\" → 502 (cấm coercion)")
    void rejectsStringValidPhrase() throws Exception {
        assertThatThrownBy(() -> grade("{\"validPhrase\":\"true\"}"))
                .isInstanceOf(AiServiceException.class);
        server.verify();
    }

    @Test
    @DisplayName("429 khi chấm điểm → 502, một request duy nhất")
    void gradeRateLimitFailsFast() throws Exception {
        server.expect(requestTo("https://ai.test/chat/completions"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client.gradePhrase("Some text to grade."))
                .isInstanceOf(AiServiceException.class);
        server.verify();
    }

    @Test
    @DisplayName("500 khi chấm điểm → 502 AiServiceException")
    void gradeServerErrorFailsFast() throws Exception {
        server.expect(requestTo("https://ai.test/chat/completions"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.gradePhrase("Some text to grade."))
                .isInstanceOf(AiServiceException.class);
        server.verify();
    }

    private String topicWordsContent() {
        return "{\"words\":[{\"english\":\"airport\",\"level\":\"B1\",\"values\":[{"
                + "\"vietnamese\":\"sân bay\","
                + "\"example\":\"The airport is busy.\","
                + "\"exampleTranslation\":\"Sân bay đông đúc.\","
                + "\"pronunciation\":\"/ˈeəpɔːt/\","
                + "\"partOfSpeech\":\"NOUN\"}]}]}";
    }

    @Test
    @DisplayName("checkTopic: payload schema đúng → trả cờ boolean, prompt mang đúng chủ đề")
    void checkTopicMapsPayload() throws Exception {
        server.expect(requestTo("https://ai.test/chat/completions"))
                .andExpect(jsonPath("$.messages[1].content").value("Topic to check: Du lịch"))
                .andRespond(withSuccess(envelope("{\"validTopic\":true}"),
                        MediaType.APPLICATION_JSON));

        assertThat(client.checkTopic("Du lịch")).isTrue();
        server.verify();
    }

    @Test
    @DisplayName("checkTopic: validTopic=false là KẾT QUẢ hợp lệ (chủ đề bị từ chối), không phải 502")
    void checkTopicFalseIsAResult() throws Exception {
        server.expect(requestTo("https://ai.test/chat/completions"))
                .andRespond(withSuccess(envelope("{\"validTopic\":false}"),
                        MediaType.APPLICATION_JSON));

        assertThat(client.checkTopic("fjaskdf")).isFalse();
        server.verify();
    }

    @Test
    @DisplayName("checkTopic: sai schema (validTopic không phải boolean) → 502")
    void checkTopicRejectsWrongSchema() throws Exception {
        server.expect(requestTo("https://ai.test/chat/completions"))
                .andRespond(withSuccess(envelope("{\"validTopic\":\"yes\"}"),
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.checkTopic("Du lịch"))
                .isInstanceOf(AiServiceException.class);
        server.verify();
    }

    @Test
    @DisplayName("checkTopic: 429 → 502, một request duy nhất")
    void checkTopicRateLimitFailsFast() {
        server.expect(requestTo("https://ai.test/chat/completions"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client.checkTopic("Du lịch"))
                .isInstanceOf(AiServiceException.class);
        server.verify();
    }

    @Test
    @DisplayName("generateTopicWords: payload hợp lệ → TopicWord đầy đủ, prompt có exclude list")
    void generateTopicWordsMapsPayload() throws Exception {
        server.expect(requestTo("https://ai.test/chat/completions"))
                .andExpect(jsonPath("$.messages[1].content")
                        .value("Topic: Du lịch\nWords the user already has: travel, hotel"))
                .andRespond(withSuccess(envelope(topicWordsContent()),
                        MediaType.APPLICATION_JSON));

        List<AiClient.TopicWord> words =
                client.generateTopicWords("Du lịch", List.of("travel", "hotel"));

        assertThat(words).hasSize(1);
        assertThat(words.get(0).english()).isEqualTo("airport");
        assertThat(words.get(0).level()).isEqualTo(Level.B1);
        assertThat(words.get(0).values()).hasSize(1);
        assertThat(words.get(0).values().get(0).vietnamese()).isEqualTo("sân bay");
        server.verify();
    }

    @Test
    @DisplayName("generateTopicWords: words rỗng/keys sai schema → 502")
    void generateTopicWordsRejectsWrongSchema() throws Exception {
        server.expect(requestTo("https://ai.test/chat/completions"))
                .andRespond(withSuccess(envelope("{\"words\":[]}"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.generateTopicWords("Du lịch", List.of()))
                .isInstanceOf(AiServiceException.class);
        server.verify();
    }

    @Test
    @DisplayName("generateTopicWords: thiếu level (bắt buộc cho Word + cache) → 502")
    void generateTopicWordsRejectsMissingLevel() throws Exception {
        server.expect(requestTo("https://ai.test/chat/completions"))
                .andRespond(withSuccess(envelope(
                        "{\"words\":[{\"english\":\"airport\",\"values\":[]}]}"),
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.generateTopicWords("Du lịch", List.of()))
                .isInstanceOf(AiServiceException.class);
        server.verify();
    }
}
