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
import com.example.english_app_cdcntt.dto.WordDto;
import com.example.english_app_cdcntt.dto.WordValueDto;
import com.example.english_app_cdcntt.enums.Level;
import com.example.english_app_cdcntt.enums.PartOfSpeech;
import com.example.english_app_cdcntt.exception.DuplicateWordException;
import com.example.english_app_cdcntt.exception.GlobalExceptionHandler;
import com.example.english_app_cdcntt.exception.InvalidRequestException;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import com.example.english_app_cdcntt.exception.OwnershipDeniedException;
import com.example.english_app_cdcntt.form.WordForm;
import com.example.english_app_cdcntt.form.WordValueForm;
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
 * MVC slice for the five word endpoints (§4.1 rows 5, 6, 8, 9, 10): the controller's thin job —
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

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new WordController(wordService))
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
}
