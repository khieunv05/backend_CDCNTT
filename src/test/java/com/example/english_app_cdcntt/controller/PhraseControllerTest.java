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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.english_app_cdcntt.config.UserPrincipal;
import com.example.english_app_cdcntt.dto.PhraseDto;
import com.example.english_app_cdcntt.exception.AiServiceException;
import com.example.english_app_cdcntt.exception.GlobalExceptionHandler;
import com.example.english_app_cdcntt.exception.InvalidPhraseException;
import com.example.english_app_cdcntt.exception.OwnershipDeniedException;
import com.example.english_app_cdcntt.form.PhraseForm;
import com.example.english_app_cdcntt.service.PhraseService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** MVC slice for the three paragraph endpoints (§4.1 rows 12, 13, 14). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PhraseControllerTest {

    private static final Long USER_ID = 7L;
    private static final Long PHRASE_ID = 42L;

    @Mock
    private PhraseService phraseService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PhraseController(phraseService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        TestingAuthenticationToken auth =
                new TestingAuthenticationToken(new UserPrincipal(USER_ID, "tester", "hash"), null, "ROLE_USER");
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private static String body() {
        return "{\"text\":\"I have went to the store yesterday and buyed some apples.\"}";
    }

    @Test
    @DisplayName("POST: 201 + SuccessResponse {message, data: PhraseDto}")
    void createReturns201() throws Exception {
        PhraseDto dto = new PhraseDto(PHRASE_ID, "I have went...", "I went...", 8,
                null, List.of());
        when(phraseService.create(eq(USER_ID), any(PhraseForm.class))).thenReturn(dto);

        mockMvc.perform(post("/api/phrases")
                        .contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Thêm đoạn văn thành công"))
                .andExpect(jsonPath("$.data.correctedText").value("I went..."))
                .andExpect(jsonPath("$.data.score").value(8));

        verify(phraseService).create(eq(USER_ID), any(PhraseForm.class));
    }

    @Test
    @DisplayName("POST text quá ngắn → 400, không đụng service")
    void createTooShortText() throws Exception {
        mockMvc.perform(post("/api/phrases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"ngan\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Đoạn văn gửi lên không hợp lệ"));

        verifyNoInteractions(phraseService);
    }

    @Test
    @DisplayName("POST AI 502 → propagate AiServiceException body")
    void createAiFailure() throws Exception {
        when(phraseService.create(eq(USER_ID), any(PhraseForm.class)))
                .thenThrow(new AiServiceException("Dịch vụ AI tạm thời không khả dụng"));

        mockMvc.perform(post("/api/phrases")
                        .contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("Dịch vụ AI tạm thời không khả dụng"));
    }

    @Test
    @DisplayName("POST không phải đoạn văn → 400 Đoạn văn gửi lên không hợp lệ")
    void createInvalidPhrase() throws Exception {
        when(phraseService.create(eq(USER_ID), any(PhraseForm.class)))
                .thenThrow(new InvalidPhraseException());

        mockMvc.perform(post("/api/phrases")
                        .contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Đoạn văn gửi lên không hợp lệ"));
    }

    @Test
    @DisplayName("GET: 200 danh sách")
    void listReturns200() throws Exception {
        when(phraseService.list(USER_ID)).thenReturn(List.of());
        mockMvc.perform(get("/api/phrases"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    @DisplayName("DELETE: 200 SuccessResponse<Void>")
    void deleteReturns200() throws Exception {
        mockMvc.perform(delete("/api/phrases/{id}", PHRASE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Xóa đoạn văn thành công"));
        verify(phraseService).delete(USER_ID, PHRASE_ID);
    }

    @Test
    @DisplayName("DELETE người khác → 403 Không có quyền xóa đoạn văn này")
    void deleteForeign403() throws Exception {
        doThrow(OwnershipDeniedException.deletePhrase())
                .when(phraseService).delete(USER_ID, PHRASE_ID);
        mockMvc.perform(delete("/api/phrases/{id}", PHRASE_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Không có quyền xóa đoạn văn này"));
    }
}
