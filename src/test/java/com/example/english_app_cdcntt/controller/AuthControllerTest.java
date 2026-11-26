package com.example.english_app_cdcntt.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.english_app_cdcntt.dto.AuthResponse;
import com.example.english_app_cdcntt.exception.DuplicateEmailException;
import com.example.english_app_cdcntt.exception.DuplicateUsernameException;
import com.example.english_app_cdcntt.exception.EmailDeliveryException;
import com.example.english_app_cdcntt.exception.GlobalExceptionHandler;
import com.example.english_app_cdcntt.exception.InvalidCredentialsException;
import com.example.english_app_cdcntt.exception.InvalidVerificationCodeException;
import com.example.english_app_cdcntt.exception.TooManyRequestsException;
import com.example.english_app_cdcntt.service.AuthService;
import com.example.english_app_cdcntt.service.PasswordResetService;
import com.example.english_app_cdcntt.service.RegistrationService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

/**
 * Controller contract of §4.1 without a Spring context: success shapes and the mapping of each
 * domain exception to its status/message. The filter chain and the 401-vs-400 split by form type
 * are covered elsewhere ({@code AuthControllerSecurityTest}, {@code GlobalExceptionHandlerTest}).
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Mock private AuthService authService;
    @Mock private RegistrationService registrationService;
    @Mock private PasswordResetService passwordResetService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuthController(authService, registrationService, passwordResetService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static MockHttpServletRequestBuilder postJson(String uri, Map<String, Object> body) {
        try {
            return post(uri).contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body));
        } catch (RuntimeException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---------- registration: step 1 ----------

    @Test
    @DisplayName("POST /register answers 200 with the code-sent message and no token")
    void registerAnswersCodeSent() throws Exception {
        mockMvc.perform(postJson("/api/auth/register", Map.of(
                        "username", "alice", "name", "Alice Nguyen",
                        "email", "alice@example.test", "password", "Passw0rd!123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(AuthController.REGISTER_CODE_SENT_MESSAGE))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist());

        verify(registrationService).requestRegistration("alice", "Alice Nguyen", "alice@example.test", "Passw0rd!123");
    }

    @Test
    @DisplayName("POST /register with a malformed email answers 400 with details")
    void registerRejectsMalformedEmail() throws Exception {
        mockMvc.perform(postJson("/api/auth/register", Map.of(
                        "username", "alice", "name", "Alice Nguyen",
                        "email", "not-an-email", "password", "Passw0rd!123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Thông tin không hợp lệ"))
                .andExpect(jsonPath("$.details[0].field").value("email"));

        verifyNoInteractions(registrationService);
    }

    @Test
    @DisplayName("a username taken by an account answers 409, not 400")
    void registerDuplicateUsernameAnswersConflict() throws Exception {
        doThrow(new DuplicateUsernameException()).when(registrationService)
                .requestRegistration(any(), any(), any(), any());

        mockMvc.perform(postJson("/api/auth/register", Map.of(
                        "username", "alice", "name", "Alice Nguyen",
                        "email", "alice@example.test", "password", "Passw0rd!123")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Tên đăng nhập đã tồn tại"));
    }

    @Test
    @DisplayName("an email already verified answers 409 with its own message")
    void registerDuplicateEmailAnswersConflict() throws Exception {
        doThrow(new DuplicateEmailException()).when(registrationService)
                .requestRegistration(any(), any(), any(), any());

        mockMvc.perform(postJson("/api/auth/register", Map.of(
                        "username", "alice", "name", "Alice Nguyen",
                        "email", "alice@example.test", "password", "Passw0rd!123")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email này đã được sử dụng"));
    }

    @Test
    @DisplayName("a resend inside the cooldown answers 429 on /register")
    void registerCooldownAnswersTooManyRequests() throws Exception {
        doThrow(new TooManyRequestsException()).when(registrationService)
                .requestRegistration(any(), any(), any(), any());

        mockMvc.perform(postJson("/api/auth/register", Map.of(
                        "username", "alice", "name", "Alice Nguyen",
                        "email", "alice@example.test", "password", "Passw0rd!123")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("Bạn đã yêu cầu quá nhiều lần, vui lòng thử lại sau"));
    }

    @Test
    @DisplayName("a mail provider failure answers 502 and the client may retry")
    void registerMailFailureAnswersBadGateway() throws Exception {
        doThrow(new EmailDeliveryException(new IllegalStateException("smtp down"))).when(registrationService)
                .requestRegistration(any(), any(), any(), any());

        mockMvc.perform(postJson("/api/auth/register", Map.of(
                        "username", "alice", "name", "Alice Nguyen",
                        "email", "alice@example.test", "password", "Passw0rd!123")))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("Không gửi được email xác thực, vui lòng thử lại"));
    }

    // ---------- registration: step 2 ----------

    @Test
    @DisplayName("POST /register/confirm answers 201 with the account-created message")
    void confirmRegistrationAnswersCreated() throws Exception {
        mockMvc.perform(postJson("/api/auth/register/confirm", Map.of("email", "alice@example.test", "code", "123456")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value(AuthController.REGISTER_SUCCESS_MESSAGE));

        verify(registrationService).confirmRegistration("alice@example.test", "123456");
    }

    @Test
    @DisplayName("a code that is not six digits is rejected before the service is called")
    void confirmRegistrationRejectsMalformedCode() throws Exception {
        mockMvc.perform(postJson("/api/auth/register/confirm", Map.of("email", "alice@example.test", "code", "12345")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("code"));

        verifyNoInteractions(registrationService);
    }

    @Test
    @DisplayName("a wrong or expired code answers one 400 message")
    void confirmRegistrationWrongCodeAnswersBadRequest() throws Exception {
        doThrow(new InvalidVerificationCodeException()).when(registrationService)
                .confirmRegistration(any(), any());

        mockMvc.perform(postJson("/api/auth/register/confirm", Map.of("email", "alice@example.test", "code", "999999")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Mã xác thực không đúng hoặc đã hết hạn"));
    }

    // ---------- password recovery ----------

    @Test
    @DisplayName("forgot-password answers 200 with the blind message")
    void forgotPasswordAnswers200() throws Exception {
        mockMvc.perform(postJson("/api/auth/forgot-password", Map.of("email", "alice@example.test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(AuthController.FORGOT_PASSWORD_MESSAGE));

        verify(passwordResetService).requestReset("alice@example.test");
    }

    @Test
    @DisplayName("forgot-password with a malformed email is still a 400 validation body")
    void forgotPasswordRejectsMalformedEmail() throws Exception {
        mockMvc.perform(postJson("/api/auth/forgot-password", Map.of("email", "nope")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("email"));

        verifyNoInteractions(passwordResetService);
    }

    @Test
    @DisplayName("forgot-password stays 200 when the service swallows every lookup failure")
    void forgotPasswordHidesUnknownEmail() throws Exception {
        mockMvc.perform(postJson("/api/auth/forgot-password", Map.of("email", "ghost@example.test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(AuthController.FORGOT_PASSWORD_MESSAGE));
    }

    @Test
    @DisplayName("reset-password answers 200 with no token in the body")
    void resetPasswordAnswers200WithoutToken() throws Exception {
        mockMvc.perform(postJson("/api/auth/reset-password", Map.of(
                        "email", "alice@example.test", "code", "123456", "newPassword", "NewPassw0rd!456")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(AuthController.RESET_PASSWORD_SUCCESS_MESSAGE))
                .andExpect(jsonPath("$.accessToken").doesNotExist());

        verify(passwordResetService).resetPassword("alice@example.test", "123456", "NewPassw0rd!456");
    }

    @Test
    @DisplayName("reset-password with the same password answers 400 instead of a silent success")
    void resetPasswordRejectsReuse() throws Exception {
        doThrow(new com.example.english_app_cdcntt.exception.PasswordReuseException()).when(passwordResetService)
                .resetPassword(any(), any(), any());

        mockMvc.perform(postJson("/api/auth/reset-password", Map.of(
                        "email", "alice@example.test", "code", "123456", "newPassword", "NewPassw0rd!456")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Mật khẩu mới phải khác mật khẩu hiện tại"));
    }

    @Test
    @DisplayName("a short new password is rejected by Bean Validation")
    void resetPasswordRejectsShortPassword() throws Exception {
        mockMvc.perform(postJson("/api/auth/reset-password", Map.of(
                        "email", "alice@example.test", "code", "123456", "newPassword", "short")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("newPassword"));

        verifyNoInteractions(passwordResetService);
    }

    // ---------- session endpoints ----------

    @Test
    @DisplayName("login returns the JWT pair")
    void loginReturnsPair() throws Exception {
        when(authService.login("alice", "Passw0rd!123")).thenReturn(new AuthResponse("acc", "ref", 900L));

        mockMvc.perform(postJson("/api/auth/login", Map.of("username", "alice", "password", "Passw0rd!123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("acc"))
                .andExpect(jsonPath("$.refreshToken").value("ref"))
                .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    @DisplayName("login with bad credentials answers 401 with the contract message")
    void loginWrongCredentialsAnswers401() throws Exception {
        when(authService.login("alice", "WrongPass!1"))
                .thenThrow(new InvalidCredentialsException());

        mockMvc.perform(postJson("/api/auth/login", Map.of("username", "alice", "password", "WrongPass!1")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Đăng nhập thất bại"));
    }

    @Test
    @DisplayName("logout answers 200 even for a token that no longer exists")
    void logoutAnswers200() throws Exception {
        mockMvc.perform(postJson("/api/auth/logout", Map.of("refreshToken", "ref")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(AuthController.LOGOUT_SUCCESS_MESSAGE));

        verify(authService).logout("ref");
    }

    @Test
    @DisplayName("change-password takes the account from the token, never from the body")
    void changePasswordUsesPrincipal() throws Exception {
        mockMvc.perform(postJson("/api/auth/change-password", Map.of(
                        "currentPassword", "Old!12345", "newPassword", "New!12345"))
                        .principal(new UsernamePasswordAuthenticationToken("alice", "hash")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(AuthController.CHANGE_PASSWORD_SUCCESS_MESSAGE));

        verify(authService).changePassword("alice", "Old!12345", "New!12345");
    }

    @Test
    @DisplayName("a reused password on change-password answers 400")
    void changePasswordRejectsReuse() throws Exception {
        doThrow(new com.example.english_app_cdcntt.exception.PasswordReuseException()).when(authService)
                .changePassword(any(), any(), any());

        mockMvc.perform(postJson("/api/auth/change-password", Map.of(
                        "currentPassword", "Old!12345", "newPassword", "Old!12345"))
                        .principal(new UsernamePasswordAuthenticationToken("alice", "hash")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Mật khẩu mới phải khác mật khẩu hiện tại"));
    }

}