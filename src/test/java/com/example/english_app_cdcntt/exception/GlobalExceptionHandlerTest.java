package com.example.english_app_cdcntt.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.english_app_cdcntt.dto.ErrorResponse;
import com.example.english_app_cdcntt.dto.FieldErrorDetail;
import com.example.english_app_cdcntt.dto.ValidationErrorResponse;
import com.example.english_app_cdcntt.form.LoginForm;
import com.example.english_app_cdcntt.form.LogoutForm;
import com.example.english_app_cdcntt.form.RefreshForm;
import com.example.english_app_cdcntt.form.RegisterForm;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Unit tests for the contract error bodies (§4, §9.2). The handler methods are package-private, so
 * this test lives in the same package and calls them directly — no Spring context required.
 *
 * <p>The 403 message is asserted as a literal because {@code RestAccessDeniedHandler.DENIED_MESSAGE}
 * is package-private in {@code com.example.english_app_cdcntt.config} and the handler keeps its own
 * copy ({@code RestAccessDeniedMessages.DENIED}); the two must stay in sync, and that equality is
 * covered by {@code SecurityErrorResponsesTest}.
 */
class GlobalExceptionHandlerTest {

    private static final String DENIED_MESSAGE = "Không có quyền truy cập";
    private static final String INVALID_INFO_MESSAGE = "Thông tin không hợp lệ";

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    /** Parameter type of the failing form is what selects 401 vs 400 — mirror that here. */
    @SuppressWarnings("unused")
    private static void loginEndpoint(LoginForm form) {
    }

    @SuppressWarnings("unused")
    private static void refreshEndpoint(RefreshForm form) {
    }

    @SuppressWarnings("unused")
    private static void registerEndpoint(RegisterForm form) {
    }

    @SuppressWarnings("unused")
    private static void logoutEndpoint(LogoutForm form) {
    }

    private MethodArgumentNotValidException validationFailure(Class<?> formType, String methodName,
                                                             String field, String message) throws Exception {
        Method method = GlobalExceptionHandlerTest.class.getDeclaredMethod(methodName, formType);
        MethodParameter parameter = new MethodParameter(method, 0);
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "form");
        binding.addError(new FieldError("form", field, null, false, null, null, message));
        return new MethodArgumentNotValidException(parameter, binding);
    }

    // ---------- validation: form type selects the shape ----------

    @Test
    @DisplayName("login form violation answers 401 Đăng nhập thất bại, not 400")
    void loginViolationAnswersUnauthorized() throws Exception {
        ResponseEntity<?> response = handler.handleValidation(
                validationFailure(LoginForm.class, "loginEndpoint", "password", "too short"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isEqualTo(new ErrorResponse("Đăng nhập thất bại"));
    }

    @Test
    @DisplayName("refresh form violation answers 401 Đăng nhập thất bại, not 400")
    void refreshViolationAnswersUnauthorized() throws Exception {
        ResponseEntity<?> response = handler.handleValidation(
                validationFailure(RefreshForm.class, "refreshEndpoint", "refreshToken", "blank"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isEqualTo(new ErrorResponse("Đăng nhập thất bại"));
    }

    @Test
    @DisplayName("register form violation answers 400 with the generic message and details")
    void registerViolationAnswersValidationBody() throws Exception {
        ResponseEntity<?> response = handler.handleValidation(
                validationFailure(RegisterForm.class, "registerEndpoint", "password", "too short"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ValidationErrorResponse body = (ValidationErrorResponse) response.getBody();
        assertThat(body.message()).isEqualTo(INVALID_INFO_MESSAGE);
        assertThat(body.details()).containsExactly(new FieldErrorDetail("password", "too short"));
    }

    @Test
    @DisplayName("logout form violation answers the generic 400 while refresh answers 401")
    void logoutViolationAnswersValidationBody() throws Exception {
        ResponseEntity<?> response = handler.handleValidation(
                validationFailure(LogoutForm.class, "logoutEndpoint", "refreshToken", "blank"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ValidationErrorResponse body = (ValidationErrorResponse) response.getBody();
        assertThat(body.message()).isEqualTo(INVALID_INFO_MESSAGE);
        assertThat(body.details()).containsExactly(new FieldErrorDetail("refreshToken", "blank"));
    }

    @Test
    @DisplayName("details are sorted by field then message, and no rejected value leaks")
    void detailsAreSortedByFieldThenMessage() throws Exception {
        Method method = GlobalExceptionHandlerTest.class.getDeclaredMethod("registerEndpoint", RegisterForm.class);
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "form");
        binding.addError(new FieldError("form", "username", "rejected-user", false, null, null, "z-message"));
        binding.addError(new FieldError("form", "username", "rejected-user", false, null, null, "a-message"));
        binding.addError(new FieldError("form", "password", "rejected-pass", false, null, null, "m-message"));
        MethodArgumentNotValidException e = new MethodArgumentNotValidException(new MethodParameter(method, 0), binding);

        ValidationErrorResponse body = (ValidationErrorResponse) handler.handleValidation(e).getBody();

        assertThat(body.details()).containsExactly(
                new FieldErrorDetail("password", "m-message"),
                new FieldErrorDetail("username", "a-message"),
                new FieldErrorDetail("username", "z-message"));
        assertThat(body.details()).noneMatch(detail -> detail.message().contains("rejected"));
    }

    // ---------- unreadable / unsupported ----------

    @Test
    @DisplayName("unreadable JSON body answers the generic 400 without details")
    void unreadableBodyAnswersGenericBadRequest() {
        ResponseEntity<ErrorResponse> response = handler.handleUnreadableBody(
                new HttpMessageNotReadableException("boom", (org.springframework.http.HttpInputMessage) null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo(new ErrorResponse(INVALID_INFO_MESSAGE));
    }

    @Test
    @DisplayName("wrong method answers 405 and wrong media type answers 415")
    void methodAndMediaTypeFailures() {
        ResponseEntity<ErrorResponse> method = handler.handleMethodOrMedia(
                new HttpRequestMethodNotSupportedException("GET"));
        ResponseEntity<ErrorResponse> media = handler.handleMethodOrMedia(
                new HttpMediaTypeNotSupportedException("text/plain"));

        assertThat(method.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(method.getBody()).isEqualTo(new ErrorResponse("Yêu cầu không hợp lệ"));
        assertThat(media.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(media.getBody()).isEqualTo(new ErrorResponse("Yêu cầu không hợp lệ"));
    }

    @Test
    @DisplayName("unknown route answers 404 with a hardcoded message")
    void noResourceAnswersNotFound() {
        ResponseEntity<ErrorResponse> response =
                handler.handleNoResource(new NoResourceFoundException(
                        org.springframework.http.HttpMethod.GET, "/api/auth/nope", null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isEqualTo(new ErrorResponse("Không tìm thấy tài nguyên"));
    }

    // ---------- domain exceptions ----------

    @Test
    @DisplayName("duplicate username answers 409 carrying the exception message")
    void duplicateUsernameAnswersConflict() {
        ResponseEntity<ErrorResponse> response =
                handler.handleDuplicateUsername(new DuplicateUsernameException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isEqualTo(new ErrorResponse("Tên đăng nhập đã tồn tại"));
    }

    @Test
    @DisplayName("invalid credentials answers 401 Đăng nhập thất bại")
    void invalidCredentialsAnswersUnauthorized() {
        ResponseEntity<ErrorResponse> response =
                handler.handleInvalidCredentials(new InvalidCredentialsException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isEqualTo(new ErrorResponse("Đăng nhập thất bại"));
    }

    @Test
    @DisplayName("expired session answers 401 Phiên đăng nhập hết hạn")
    void tokenExpiredAnswersUnauthorized() {
        ResponseEntity<ErrorResponse> response =
                handler.handleTokenExpired(new TokenExpiredException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isEqualTo(new ErrorResponse("Phiên đăng nhập hết hạn"));
    }

    @Test
    @DisplayName("access denied answers 403 with the contract message")
    void accessDeniedAnswersForbidden() {
        ResponseEntity<ErrorResponse> response =
                handler.handleAccessDenied(new AccessDeniedException("denied"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isEqualTo(new ErrorResponse(DENIED_MESSAGE));
    }

    @Test
    @DisplayName("unexpected failure answers a 500 with no internal detail")
    void unexpectedAnswersInternalServerError() {
        ResponseEntity<ErrorResponse> response =
                handler.handleUnexpected(new IllegalStateException("jdbc:mysql://secret-host/db"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isEqualTo(new ErrorResponse("Lỗi hệ thống"));
        assertThat(response.getBody().message()).doesNotContain("secret-host");
    }

    @Test
    @DisplayName("every handled response carries exactly one message and no stack trace field")
    void responsesCarryNoStackTrace() {
        List<ResponseEntity<?>> responses = List.of(
                handler.handleDuplicateUsername(new DuplicateUsernameException()),
                handler.handleInvalidCredentials(new InvalidCredentialsException()),
                handler.handleTokenExpired(new TokenExpiredException()),
                handler.handleAccessDenied(new AccessDeniedException("denied")),
                handler.handleUnexpected(new IllegalStateException("boom")));

        for (ResponseEntity<?> response : responses) {
            assertThat(response.getBody()).isInstanceOf(ErrorResponse.class);
            assertThat(((ErrorResponse) response.getBody()).message()).isNotBlank();
        }
    }
}
