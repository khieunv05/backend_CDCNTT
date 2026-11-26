package com.example.english_app_cdcntt.controller;

import com.example.english_app_cdcntt.dto.MessageResponse;
import com.example.english_app_cdcntt.exception.TooManyRequestsException;
import com.example.english_app_cdcntt.service.IpRateLimiter;
import tools.jackson.databind.json.JsonMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Per-IP throttle for the two unauthenticated endpoints that can send mail. It lives in the HTTP
 * layer because the answer differs by path: a throttled {@code /register} returns 429 so the
 * client backs off (§5.2), while a throttled {@code /forgot-password} keeps the blinding 200 body
 * of the happy path so the limit never reveals that an email exists. The service layer stays
 * unaware of HTTP status codes and of client addresses.
 *
 * <p>Runs after the security filter chain (a plain {@code @Component} filter keeps the default
 * lowest precedence), so the paths here are already {@code permitAll} and never require a token.
 */
@Component
public class IpRateLimitFilter extends OncePerRequestFilter {

    static final String REGISTER_PATH = "/api/auth/register";
    static final String FORGOT_PASSWORD_PATH = "/api/auth/forgot-password";

    /** {@code HttpServletResponse} does not expose a constant for this status in Servlet 6. */
    private static final int TOO_MANY_REQUESTS = 429;

    private final IpRateLimiter ipRateLimiter;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public IpRateLimitFilter(IpRateLimiter ipRateLimiter) {
        this.ipRateLimiter = ipRateLimiter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }
        String path = request.getRequestURI();
        if (REGISTER_PATH.equals(path)) {
            if (!ipRateLimiter.tryAcquire(request.getRemoteAddr())) {
                writeJson(response, TOO_MANY_REQUESTS, TooManyRequestsException.MESSAGE);
                return;
            }
        } else if (FORGOT_PASSWORD_PATH.equals(path)
                && !ipRateLimiter.tryAcquire(request.getRemoteAddr())) {
            // Same body as the happy path: even the throttle must not reveal that the email exists.
            writeJson(response, HttpServletResponse.SC_OK, AuthController.FORGOT_PASSWORD_MESSAGE);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void writeJson(HttpServletResponse response, int status, String message)
            throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        jsonMapper.writeValue(response.getWriter(), new MessageResponse(message));
    }
}
