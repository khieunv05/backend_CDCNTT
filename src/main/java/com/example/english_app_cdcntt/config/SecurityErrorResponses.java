package com.example.english_app_cdcntt.config;

import com.example.english_app_cdcntt.dto.ErrorResponse;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/**
 * Writes the contract's generic JSON error straight to the response — shared by the JWT filter,
 * the entry point and the access-denied handler so all three produce byte-identical bodies.
 */
final class SecurityErrorResponses {

    private SecurityErrorResponses() {
    }

    static void write(HttpServletResponse response, ObjectMapper objectMapper, HttpStatus status, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(new ErrorResponse(message)));
    }
}
