package com.example.english_app_cdcntt.config;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

/** 403 for authenticated-but-denied access (default-deny routes, later ownership rules) — generic JSON per §9.2. */
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    static final String DENIED_MESSAGE = "Không có quyền truy cập";

    private final ObjectMapper objectMapper;

    public RestAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        SecurityErrorResponses.write(response, objectMapper, HttpStatus.FORBIDDEN, DENIED_MESSAGE);
    }
}
