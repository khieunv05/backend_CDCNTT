package com.example.english_app_cdcntt.exception;

import com.example.english_app_cdcntt.dto.ErrorResponse;
import com.example.english_app_cdcntt.dto.FieldErrorDetail;
import com.example.english_app_cdcntt.dto.ValidationErrorResponse;
import com.example.english_app_cdcntt.form.LoginForm;
import com.example.english_app_cdcntt.form.RefreshForm;
import java.util.Comparator;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Maps application exceptions to the exact contract JSON (§4, §9.2). Every message is hardcoded
 * here — framework text never reaches the client: no stack traces, no JDBC details, no
 * ProblemDetail bodies. Form violations on login/refresh answer the credentials 401 instead of
 * a 400 (§5.2); the failing form type makes the distinction.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    static final String INVALID_INFO_MESSAGE = "Thông tin không hợp lệ";

    /** Constraint name from V1__init_schema.sql:21 — the only integrity violation mapped to 409. */
    private static final String WORDS_ENGLISH_UNIQUE_CONSTRAINT = "uk_words_user_english";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<?> handleValidation(MethodArgumentNotValidException e) {
        Class<?> formType = e.getParameter().getParameterType();
        if (formType == LoginForm.class || formType == RefreshForm.class) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Đăng nhập thất bại"));
        }
        List<FieldErrorDetail> details = e.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> new FieldErrorDetail(fieldError.getField(), fieldError.getDefaultMessage()))
                .sorted(Comparator.comparing(FieldErrorDetail::field)
                        .thenComparing(FieldErrorDetail::message,
                                Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList();
        return ResponseEntity.badRequest()
                .body(new ValidationErrorResponse(INVALID_INFO_MESSAGE, details));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException e) {
        // Malformed JSON or wrong value type → generic 400 without details (§9.2), on every endpoint.
        return ResponseEntity.badRequest().body(new ErrorResponse(INVALID_INFO_MESSAGE));
    }

    @ExceptionHandler(DuplicateUsernameException.class)
    ResponseEntity<ErrorResponse> handleDuplicateUsername(DuplicateUsernameException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(TokenExpiredException.class)
    ResponseEntity<ErrorResponse> handleTokenExpired(TokenExpiredException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(DuplicateWordException.class)
    ResponseEntity<ErrorResponse> handleDuplicateWord(DuplicateWordException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(e.getMessage()));
    }

    /**
     * §9.2 — a word/phrase/review denial carries its own §4.1 message; this handler is registered
     * before the generic {@link AccessDeniedException} one so the contract text wins while
     * framework denials keep the generic message.
     */
    @ExceptionHandler(OwnershipDeniedException.class)
    ResponseEntity<ErrorResponse> handleOwnershipDenied(OwnershipDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse(e.getMessage()));
    }

    /** §6.2 — a rule Bean Validation cannot express: generic 400 body with one details entry. */
    @ExceptionHandler(InvalidRequestException.class)
    ResponseEntity<ValidationErrorResponse> handleInvalidRequest(InvalidRequestException e) {
        return ResponseEntity.badRequest().body(new ValidationErrorResponse(INVALID_INFO_MESSAGE,
                List.of(new FieldErrorDetail(e.field(), e.getMessage()))));
    }

    /**
     * §6.2:287 — the create/update race ends here: only the words UNIQUE key becomes the contract
     * 409, every other integrity failure stays a generic 500 instead of pretending to be a duplicate.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ErrorResponse> handleIntegrityViolation(DataIntegrityViolationException e) {
        if (mentionsConstraint(e, WORDS_ENGLISH_UNIQUE_CONSTRAINT)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse(new DuplicateWordException().getMessage()));
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("Lỗi hệ thống"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e) {
        // Controller-level denials; filter-level denials never reach the advice and are answered
        // by RestAccessDeniedHandler with the same body.
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse(RestAccessDeniedMessages.DENIED));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("Không tìm thấy tài nguyên"));
    }

    /** §9.2 — a non-numeric path variable is a client mistake, so it must answer 400, never a 500. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(INVALID_INFO_MESSAGE));
    }

    @ExceptionHandler({HttpRequestMethodNotSupportedException.class,
            HttpMediaTypeNotSupportedException.class})
    ResponseEntity<ErrorResponse> handleMethodOrMedia(Exception e) {
        HttpStatus status = e instanceof HttpMediaTypeNotSupportedException
                ? HttpStatus.UNSUPPORTED_MEDIA_TYPE
                : HttpStatus.METHOD_NOT_ALLOWED;
        return ResponseEntity.status(status).body(new ErrorResponse("Yêu cầu không hợp lệ"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Lỗi hệ thống"));
    }

    /** Walks the cause chain looking for the named constraint, like the register duplicate check. */
    private static boolean mentionsConstraint(Throwable failure, String constraint) {
        for (Throwable t = failure; t != null; t = t.getCause()) {
            if (t.getMessage() != null && t.getMessage().contains(constraint)) {
                return true;
            }
        }
        return false;
    }

    /** Kept in sync with {@code RestAccessDeniedHandler.DENIED_MESSAGE} (different package visibility). */
    private static final class RestAccessDeniedMessages {
        static final String DENIED = "Không có quyền truy cập";

        private RestAccessDeniedMessages() {
        }
    }
}
