package com.example.english_app_cdcntt.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.english_app_cdcntt.exception.DuplicateUsernameException;
import com.example.english_app_cdcntt.exception.GlobalExceptionHandler;
import com.example.english_app_cdcntt.exception.InvalidCredentialsException;
import com.example.english_app_cdcntt.exception.TokenExpiredException;
import com.example.english_app_cdcntt.service.AuthService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthControllerTest {

    private static final String REGISTER_SUCCESS = "Tạo tài khoản thành công";
    private static final String LOGOUT_SUCCESS = "Đăng xuất thành công";

    @Mock
    private AuthService authService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /api/auth/register returns 201 and the success message")
    void registerReturnsCreated() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"s3cret!x\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value(REGISTER_SUCCESS));

        verify(authService).register("alice", "s3cret!x");
    }

    @Test
    @DisplayName("POST /api/auth/login returns 200 with both tokens and expiresIn")
    void loginReturnsTokens() throws Exception {
        org.mockito.Mockito.when(authService.login(anyString(), anyString()))
                .thenReturn(new com.example.english_app_cdcntt.dto.AuthResponse("a.b.c", "r.e.f", 900L));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"s3cret!x\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("a.b.c"))
                .andExpect(jsonPath("$.refreshToken").value("r.e.f"))
                .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    @DisplayName("POST /api/auth/logout returns 200 and the logout message")
    void logoutReturnsMessage() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"r.e.f\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(LOGOUT_SUCCESS));

        verify(authService).logout("r.e.f");
    }

    @Test
    @DisplayName("POST /api/auth/refresh returns 200 with a rotated pair")
    void refreshReturnsRotatedPair() throws Exception {
        org.mockito.Mockito.when(authService.refresh(anyString()))
                .thenReturn(new com.example.english_app_cdcntt.dto.AuthResponse("n.e.w", "n.r.t", 900L));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"r.e.f\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("n.e.w"))
                .andExpect(jsonPath("$.refreshToken").value("n.r.t"));
    }

    @Test
    @DisplayName("register maps DuplicateUsernameException to 409")
    void registerDuplicateIsConflict() throws Exception {
        doThrow(new DuplicateUsernameException())
                .when(authService).register(anyString(), anyString());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"s3cret!x\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("login maps InvalidCredentialsException to 401")
    void loginInvalidIsUnauthorized() throws Exception {
        org.mockito.Mockito.when(authService.login(anyString(), anyString()))
                .thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"wrong!pass\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("refresh maps TokenExpiredException to 401")
    void refreshExpiredIsUnauthorized() throws Exception {
        org.mockito.Mockito.when(authService.refresh(anyString()))
                .thenThrow(new TokenExpiredException());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"r.e.f\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("blank username on login is rejected with the credentials 401 before the service is called")
    void blankUsernameIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"  \",\"password\":\"s3cret!x\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Đăng nhập thất bại"));

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("blank username on register is rejected with a 400 validation body before the service is called")
    void blankUsernameOnRegisterIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"  \",\"password\":\"s3cret!x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details").isArray());

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("malformed JSON body is rejected with 400")
    void malformedBodyIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }
}
