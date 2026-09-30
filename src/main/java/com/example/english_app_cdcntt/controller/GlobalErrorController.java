package com.example.english_app_cdcntt.controller;

import com.example.english_app_cdcntt.dto.ErrorResponse;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Replaces Boot's BasicErrorController so container-level failures (an infrastructure exception
 * propagated out of the JWT filter, any {@code sendError}) still answer the contract's generic
 * JSON instead of the default whitelabel/ProblemDetail body with framework internals (§9.2).
 * Most exceptions are resolved earlier by
 * {@link com.example.english_app_cdcntt.exception.GlobalExceptionHandler}; this only handles
 * ERROR dispatches. {@code /error} is permitAll in SecurityConfig because the ERROR dispatch
 * passes through the security filter chain.
 */
@Controller
public class GlobalErrorController implements ErrorController {

    @RequestMapping("/error")
    ResponseEntity<ErrorResponse> handleError(HttpServletRequest request) {
        Object statusCode = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = statusCode instanceof Integer code ? code : HttpStatus.INTERNAL_SERVER_ERROR.value();
        HttpStatus resolved = HttpStatus.resolve(status);
        if (resolved == null) {
            return ResponseEntity.internalServerError().body(new ErrorResponse("Lỗi hệ thống"));
        }
        String message;
        if (resolved == HttpStatus.NOT_FOUND) {
            message = "Không tìm thấy tài nguyên";
        } else if (resolved.is4xxClientError()) {
            message = "Yêu cầu không hợp lệ";
        } else {
            message = "Lỗi hệ thống";
        }
        return ResponseEntity.status(resolved).body(new ErrorResponse(message));
    }
}
