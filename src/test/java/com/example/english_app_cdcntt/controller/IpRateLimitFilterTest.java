package com.example.english_app_cdcntt.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.english_app_cdcntt.exception.TooManyRequestsException;
import com.example.english_app_cdcntt.service.IpRateLimiter;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
class IpRateLimitFilterTest {

    private static final String CLIENT_IP = "10.0.0.1";

    @Mock private IpRateLimiter ipRateLimiter;

    private IpRateLimitFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new IpRateLimitFilter(ipRateLimiter);
        request = new MockHttpServletRequest();
        request.setRemoteAddr(CLIENT_IP);
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
    }

    @Test
    @DisplayName("a throttled POST /register answers 429 with the contract message and stops the chain")
    void doFilter_registerOverLimit_answers429() throws Exception {
        request.setMethod("POST");
        request.setRequestURI("/api/auth/register");
        when(ipRateLimiter.tryAcquire(CLIENT_IP)).thenReturn(false);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentAsString(StandardCharsets.UTF_8))
                .isEqualTo("{\"message\":\"" + TooManyRequestsException.MESSAGE + "\"}");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("a register request under the limit keeps going to the controller")
    void doFilter_registerUnderLimit_continuesChain() throws Exception {
        request.setMethod("POST");
        request.setRequestURI("/api/auth/register");
        when(ipRateLimiter.tryAcquire(CLIENT_IP)).thenReturn(true);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("a throttled POST /forgot-password stays 200 with the blind message, not 429")
    void doFilter_forgotPasswordOverLimit_staysBlind() throws Exception {
        request.setMethod("POST");
        request.setRequestURI("/api/auth/forgot-password");
        when(ipRateLimiter.tryAcquire(CLIENT_IP)).thenReturn(false);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString(StandardCharsets.UTF_8))
                .contains(AuthController.FORGOT_PASSWORD_MESSAGE);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("a forgot-password request under the limit keeps going to the controller")
    void doFilter_forgotPasswordUnderLimit_continuesChain() throws Exception {
        request.setMethod("POST");
        request.setRequestURI("/api/auth/forgot-password");
        when(ipRateLimiter.tryAcquire(CLIENT_IP)).thenReturn(true);

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("other paths and verbs are never throttled")
    void doFilter_otherRequest_skipsLimiter() throws Exception {
        request.setMethod("GET");
        request.setRequestURI("/api/auth/register");

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        verify(ipRateLimiter, never()).tryAcquire(anyString());
    }
}
