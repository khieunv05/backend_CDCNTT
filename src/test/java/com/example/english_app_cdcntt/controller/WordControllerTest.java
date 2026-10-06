package com.example.english_app_cdcntt.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.english_app_cdcntt.config.UserPrincipal;
import com.example.english_app_cdcntt.dto.DueCountResponse;
import com.example.english_app_cdcntt.dto.GeneratedWordDto;
import com.example.english_app_cdcntt.dto.ReviewResultResponse;
import com.example.english_app_cdcntt.dto.WordDto;
import com.example.english_app_cdcntt.dto.WordValueDto;
import com.example.english_app_cdcntt.enums.Level;
import com.example.english_app_cdcntt.enums.PartOfSpeech;
import com.example.english_app_cdcntt.exception.DuplicateWordException;
import com.example.english_app_cdcntt.exception.GlobalExceptionHandler;
import com.example.english_app_cdcntt.exception.AiServiceException;
import com.example.english_app_cdcntt.exception.InvalidRequestException;
import com.example.english_app_cdcntt.exception.InvalidTopicException;
import com.example.english_app_cdcntt.exception.InvalidWordException;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import com.example.english_app_cdcntt.exception.OwnershipDeniedException;
import com.example.english_app_cdcntt.form.TopicForm;
import com.example.english_app_cdcntt.form.WordForm;
import com.example.english_app_cdcntt.form.ReviewForm;
import com.example.english_app_cdcntt.form.WordValueForm;
import com.example.english_app_cdcntt.service.GenerateService;
import com.example.english_app_cdcntt.service.TopicGenerateService;
import com.example.english_app_cdcntt.service.WordService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * MVC slice for the six word endpoints (§4.1 rows 5, 6, 8, 9, 10, 15): the controller's thin job —
 * contract JSON, status codes and the authenticated owner id — verified through MockMvc with the
 * {@link GlobalExceptionHandler} registered, so error bodies follow §4 exactly. Business rules live
 * in {@code WordServiceImplTest}; the service is a Mockito mock here.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WordControllerTest {

    private static final Long USER_ID = 7L;
    private static final Long WORD_ID = 42L;

    @Mock
    private WordService wordService;

    @Mock
    private GenerateService generateService;

    @Mock
    private TopicGenerateService topicGenerateService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new WordController(wordService, generateService, topicGenerateService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(new UserPrincipal(USER_ID, "alice", "hash"), null, "ROLE_USER"));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static WordDto wordDto() {
        return new WordDto(WORD_ID, "hello", null, 0, Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"),
                List.of(new WordValueDto(1L, "xin chào", "Hello world", "Chào thế giới",
                        "/həˈləʊ/", PartOfSpeech.NOUN)));
    }

    /** Confirm-payload builder mirroring createWord semantics (nested value id is ignored). */
    private static WordForm wordForm(String english, Level level, String vietnamese) {
        return new WordForm(english, level, List.of(new WordValueForm(
                null, vietnamese, "example", "dịch", "/ipa/", PartOfSpeech.NOUN)));
    }

    private static String createBody(String english) {
        return """
                {"english":"%s","level":null,"values":[{"id":null,"vietnamese":"xin chào",
                "example":null,"exampleTranslation":null,"pronunciation":null,"partOfSpeech":null}]}
                """.formatted(english);
    }

    @Test
    @DisplayName("GET /api/words returns the notebook array unwrapped")
    void getWords_returnsNotebookArray() throws Exception {
        when(wordService.listWords(USER_ID)).thenReturn(List.of(wordDto()));

        mockMvc.perform(get("/api/words"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(42))
                .andExpect(jsonPath("$[0].english").value("hello"))
                .andExpect(jsonPath("$[0].values[0].vietnamese").value("xin chào"));
    }

    @Test
    @DisplayName("GET /api/words/due-count returns the bare count object")
    void getDueCount_returnsCountObject() throws Exception {
        when(wordService.countDue(USER_ID)).thenReturn(new DueCountResponse(3));

        mockMvc.perform(get("/api/words/due-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dueCount").value(3));
    }

    @Test
    @DisplayName("POST /api/words answers 201 with message and data")
    void postWord_returns201WithBody() throws Exception {
        when(wordService.createWord(eq(USER_ID), any(WordForm.class))).thenReturn(wordDto());

        mockMvc.perform(post("/api/words")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("  HeLLo  ")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Thêm từ thành công"))
                .andExpect(jsonPath("$.data.english").value("hello"))
                .andExpect(jsonPath("$.data.values[0].example").value("Hello world"));

        ArgumentCaptor<WordForm> captor = ArgumentCaptor.forClass(WordForm.class);
        verify(wordService).createWord(eq(USER_ID), captor.capture());
        assertThat(captor.getValue().english()).isEqualTo("  HeLLo  ");
    }

    @Test
    @DisplayName("PUT /api/words/{id} passes the path id and principal id to the service")
    void putWord_passesPathAndPrincipalIds() throws Exception {
        when(wordService.updateWord(eq(USER_ID), eq(WORD_ID), any(WordForm.class))).thenReturn(wordDto());

        mockMvc.perform(put("/api/words/{id}", WORD_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("hello")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Sửa từ thành công"))
                .andExpect(jsonPath("$.data.id").value(42));

        verify(wordService).updateWord(eq(USER_ID), eq(WORD_ID), any(WordForm.class));
    }

    @Test
    @DisplayName("DELETE /api/words/{id} answers the delete message")
    void deleteWord_returnsMessage() throws Exception {
        mockMvc.perform(delete("/api/words/{id}", WORD_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Xóa từ thành công"));

        verify(wordService).deleteWord(USER_ID, WORD_ID);
    }

    @Test
    @DisplayName("a duplicate key on POST or PUT answers 409 with the contract message")
    void duplicateKey_answers409() throws Exception {
        when(wordService.createWord(eq(USER_ID), any(WordForm.class)))
                .thenThrow(new DuplicateWordException());

        mockMvc.perform(post("/api/words")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("hello")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Từ này đã có trong sổ của bạn"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("a foreign word id on PUT and DELETE answers 403 with the per-operation message")
    void foreignWord_answers403WithOperationMessage() throws Exception {
        when(wordService.updateWord(eq(USER_ID), eq(WORD_ID), any(WordForm.class)))
                .thenThrow(OwnershipDeniedException.updateWord());
        doThrow(OwnershipDeniedException.deleteWord()).when(wordService).deleteWord(USER_ID, WORD_ID);

        mockMvc.perform(put("/api/words/{id}", WORD_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("hello")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Không có quyền sửa từ này"));

        mockMvc.perform(delete("/api/words/{id}", WORD_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Không có quyền xóa từ này"));
    }

    @Test
    @DisplayName("a meaning id on POST create answers the 400 details body with the values[].id field")
    void meaningIdOnCreate_answers400Details() throws Exception {
        when(wordService.createWord(eq(USER_ID), any(WordForm.class)))
                .thenThrow(InvalidRequestException.meaningIdOnCreate());

        mockMvc.perform(post("/api/words")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("hello")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Thông tin không hợp lệ"))
                .andExpect(jsonPath("$.details[0].field").value("values[].id"))
                .andExpect(jsonPath("$.details[0].message").value("Không được gửi ID nghĩa khi thêm từ"));
    }

    @Test
    @DisplayName("a blank english answers 400 with the bean validation field error")
    void blankEnglish_answers400FieldError() throws Exception {
        mockMvc.perform(post("/api/words")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("   ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Thông tin không hợp lệ"))
                .andExpect(jsonPath("$.details[0].field").value("english"));

        verifyNoInteractions(wordService);
    }

    @Test
    @DisplayName("an empty values list answers 400 before the service is touched")
    void emptyValues_answers400() throws Exception {
        mockMvc.perform(post("/api/words")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"english":"hello","level":null,"values":[]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Thông tin không hợp lệ"));

        verifyNoInteractions(wordService);
    }

    @Test
    @DisplayName("a non-numeric word id answers 400, never 500")
    void nonNumericWordId_answers400() throws Exception {
        mockMvc.perform(put("/api/words/{id}", "abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("hello")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Thông tin không hợp lệ"));

        verifyNoInteractions(wordService);
    }

    @Test
    @DisplayName("a malformed JSON body answers the generic 400 without details")
    void malformedJson_answers400Generic() throws Exception {
        mockMvc.perform(post("/api/words")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"english\": not json}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Thông tin không hợp lệ"))
                .andExpect(jsonPath("$.details").doesNotExist());

        verifyNoInteractions(wordService);
    }

    // ---- GET /api/words/generate (§4.1 row 7) ----

    private static GeneratedWordDto generatedWordDto() {
        return new GeneratedWordDto("banana", Level.B1, List.of(
                new WordValueDto(null, "quả chuối", "a banana", "một quả chuối", "/bəˈnɑː.nə/", PartOfSpeech.NOUN)));
    }

    @Test
    @DisplayName("generate: cache/AI result answers 200 with null ids and the raw english")
    void generate_answers200() throws Exception {
        when(generateService.generateWord("banana")).thenReturn(generatedWordDto());

        mockMvc.perform(get("/api/words/generate").param("english", "banana"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.english").value("banana"))
                .andExpect(jsonPath("$.level").value("B1"))
                .andExpect(jsonPath("$.values[0].id").doesNotExist())
                .andExpect(jsonPath("$.values[0].vietnamese").value("quả chuối"));

        verify(generateService).generateWord("banana");
        verifyNoInteractions(wordService);
    }

    @Test
    @DisplayName("generate: a non-word answers 400 with the §4.1 text")
    void generate_nonWord_answers400() throws Exception {
        when(generateService.generateWord("abc123")).thenThrow(new InvalidWordException());

        mockMvc.perform(get("/api/words/generate").param("english", "abc123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Từ hoặc cụm từ gửi lên không phải một từ tiếng Anh hợp lệ"));

        verifyNoInteractions(wordService);
    }

    @Test
    @DisplayName("generate: a phrase (make up) passes through to the service and answers 200")
    void generate_phrase_answers200() throws Exception {
        when(generateService.generateWord("make up")).thenReturn(
                new GeneratedWordDto("make up", Level.B1, List.of()));

        mockMvc.perform(get("/api/words/generate").param("english", "make up"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.english").value("make up"));
    }

    @Test
    @DisplayName("generate: an AI infrastructure failure answers 502 with the §4.1 text")
    void generate_aiFailure_answers502() throws Exception {
        when(generateService.generateWord("banana")).thenThrow(new AiServiceException("provider 503"));

        mockMvc.perform(get("/api/words/generate").param("english", "banana"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("Dịch vụ AI tạm thời không khả dụng"));

        verifyNoInteractions(wordService);
    }

    @Test
    @DisplayName("generate: a missing english query parameter answers 400, never 500")
    void generate_missingParam_answers400() throws Exception {
        mockMvc.perform(get("/api/words/generate"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Thông tin không hợp lệ"));

        verifyNoInteractions(generateService, wordService);
    }

    @Test
    @DisplayName("review: 200 + distinct updatedCount from the service (§4.1 row 11)")
    void review_answers200WithDistinctCount() throws Exception {
        when(wordService.review(eq(USER_ID), any(ReviewForm.class)))
                .thenReturn(new ReviewResultResponse(2));

        mockMvc.perform(post("/api/words/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"wordIds\":[7,7,9]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewedCount").value(2));
    }

    @Test
    @DisplayName("review: a foreign or unknown id answers 403 with the §4.1 text")
    void review_foreignId_answers403() throws Exception {
        when(wordService.review(eq(USER_ID), any(ReviewForm.class)))
                .thenThrow(OwnershipDeniedException.review());

        mockMvc.perform(post("/api/words/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"wordIds\":[7]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Không có quyền với từ không thuộc sở hữu"));
    }

    @Test
    @DisplayName("review: empty wordIds answers 400 before touching the service")
    void review_emptyIds_answers400() throws Exception {
        mockMvc.perform(post("/api/words/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"wordIds\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Thông tin không hợp lệ"));

        verifyNoInteractions(wordService);
    }

    @Test
    void review_nullElement_answers400() throws Exception {
        mockMvc.perform(post("/api/words/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"wordIds\":[7,null]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Thông tin không hợp lệ"));

        verifyNoInteractions(wordService);
    }

    @Test
    void review_overLimit_answers400() throws Exception {
        String ids = java.util.stream.LongStream.rangeClosed(1, 501)
                .mapToObj(Long::toString)
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
        mockMvc.perform(post("/api/words/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"wordIds\":" + ids + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Thông tin không hợp lệ"));

        verifyNoInteractions(wordService);
    }

    @Test
    @DisplayName("POST /api/words/generate-topic returns 200 with the proposal message and cached proposals")
    void generateTopic_returns200WithProposals() throws Exception {
        when(topicGenerateService.generateTopicWords(USER_ID, "Travel"))
                .thenReturn(List.of(new GeneratedWordDto("hello", Level.A1, List.of())));

        mockMvc.perform(post("/api/words/generate-topic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topic\":\"Travel\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đã sinh từ theo chủ đề"))
                .andExpect(jsonPath("$.data[0].english").value("hello"));
    }

    @Test
    @DisplayName("generate-topic: blank topic answers 400 before touching the service")
    void generateTopic_blank_answers400() throws Exception {
        mockMvc.perform(post("/api/words/generate-topic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topic\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Thông tin không hợp lệ"));

        verifyNoInteractions(topicGenerateService);
    }

    @Test
    @DisplayName("generate-topic: AI rejects the topic → 400 with the act-20 text, no service call result")
    void generateTopic_aiRejected_answers400() throws Exception {
        when(topicGenerateService.generateTopicWords(USER_ID, "fjaskdf"))
                .thenThrow(new InvalidTopicException());

        mockMvc.perform(post("/api/words/generate-topic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topic\":\"fjaskdf\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Chủ đề không hợp lệ, vui lòng nhập lại"));
    }

    @Test
    @DisplayName("generate-topic: AI failure → 502 before any DB change")
    void generateTopic_aiFailure_answers502() throws Exception {
        when(topicGenerateService.generateTopicWords(USER_ID, "Travel"))
                .thenThrow(new AiServiceException("upstream 500"));

        mockMvc.perform(post("/api/words/generate-topic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topic\":\"Travel\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("Dịch vụ AI tạm thời không khả dụng"));
    }

    @Test
    @DisplayName("POST /api/words/generate-topic/confirm returns 201 with the picked words added")
    void confirmTopic_returns201WithAddedWords() throws Exception {
        when(topicGenerateService.confirmTopicWords(eq(USER_ID), eq(List.of(
                wordForm("airport", Level.B1, "sân bay"),
                wordForm("hotel", Level.A2, "khách sạn")))))
                .thenReturn(List.of(wordDto()));

        mockMvc.perform(post("/api/words/generate-topic/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"words":[
                                  {"english":"airport","level":"B1","values":[
                                    {"vietnamese":"sân bay","example":"example","exampleTranslation":"dịch",
                                     "pronunciation":"/ipa/","partOfSpeech":"NOUN"}]},
                                  {"english":"hotel","level":"A2","values":[
                                    {"vietnamese":"khách sạn","example":"example","exampleTranslation":"dịch",
                                     "pronunciation":"/ipa/","partOfSpeech":"NOUN"}]}]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Thêm từ theo chủ đề thành công"))
                .andExpect(jsonPath("$.data[0].english").value("hello"));
    }

    @Test
    @DisplayName("confirm: empty picked list answers 400 before touching the service")
    void confirmTopic_emptyList_answers400() throws Exception {
        mockMvc.perform(post("/api/words/generate-topic/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"words\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Thông tin không hợp lệ"));

        verifyNoInteractions(topicGenerateService);
    }

    @Test
    @DisplayName("confirm: an invalid picked word → 400 with the §7 text")
    void confirmTopic_invalidWord_answers400() throws Exception {
        when(topicGenerateService.confirmTopicWords(eq(USER_ID),
                eq(List.of(wordForm("not a word!", Level.A2, "sai")))))
                .thenThrow(new InvalidWordException());

        mockMvc.perform(post("/api/words/generate-topic/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"words\":[{\"english\":\"not a word!\",\"level\":\"A2\",\"values\":["
                                + "{\"vietnamese\":\"sai\",\"example\":\"example\",\"exampleTranslation\":\"dịch\","
                                + "\"pronunciation\":\"/ipa/\",\"partOfSpeech\":\"NOUN\"}]}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Từ hoặc cụm từ gửi lên không phải một từ tiếng Anh hợp lệ"));
    }

    @Test
    @DisplayName("confirm: more than 20 picked words → 400 validation, service untouched")
    void confirmTopic_moreThan20Words_answers400() throws Exception {
        String word = "{\"english\":\"airport\",\"level\":\"B1\",\"values\":["
                + "{\"vietnamese\":\"sân bay\",\"example\":\"I fly.\",\"exampleTranslation\":\"Tôi bay.\","
                + "\"pronunciation\":\"/ipa/\",\"partOfSpeech\":\"NOUN\"}]}";
        String body = "{\"words\":[" + String.join(",", java.util.Collections.nCopies(21, word)) + "]}";

        mockMvc.perform(post("/api/words/generate-topic/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Thông tin không hợp lệ"));

        verifyNoInteractions(topicGenerateService);
    }
}
