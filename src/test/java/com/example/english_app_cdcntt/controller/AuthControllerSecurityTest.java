package com.example.english_app_cdcntt.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.english_app_cdcntt.config.AppUserDetailsService;
import com.example.english_app_cdcntt.config.JwtService;
import com.example.english_app_cdcntt.config.SecurityConfig;
import com.example.english_app_cdcntt.config.TokenType;
import com.example.english_app_cdcntt.config.UserPrincipal;
import com.example.english_app_cdcntt.dto.AuthResponse;
import com.example.english_app_cdcntt.exception.GlobalExceptionHandler;
import com.example.english_app_cdcntt.service.AuthService;
import io.jsonwebtoken.MalformedJwtException;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * MVC slice for the §5.3 filter chain (plan §12: “import security config/filter”), which
 * {@code AuthControllerTest} does not cover because it builds standalone MockMvc without security.
 * These tests are the automated proof for {@code JwtAuthenticationFilter}, the entry point and the
 * access-denied handler: permitAll endpoints stay reachable, a present-but-bad Bearer token is
 * rejected everywhere, and an authenticated request reaches the route rules.
 *
 * <p>No database, no AI: {@code JwtService} and {@code AppUserDetailsService} are mocked.
 */
@WebMvcTest(controllers = AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AuthControllerSecurityTest {

    private static final String BEARER = "Bearer ";
    private static final String INVALID_TOKEN_MESSAGE = "Token không hợp lệ hoặc hết hạn";
    private static final String LOGIN_BODY = "{\"username\":\"alice\",\"password\":\"s3cret!x\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AppUserDetailsService userDetailsService;

    private static JwtService.JwtPayload payload(TokenType type, Long userId, String username) {
        return new JwtService.JwtPayload(username, userId, type, "jti-1",
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:30:00Z"));
    }

    private void stubMatchingAccount(String token, Long userId, String username) {
        when(jwtService.parse(token)).thenReturn(payload(TokenType.ACCESS, userId, username));
        when(userDetailsService.loadUserByUsername(username))
                .thenReturn(new UserPrincipal(userId, username, "hash"));
    }

    @Test
    @DisplayName("protected /api/** without a Bearer token answers the entry point 401")
    void protectedEndpointWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/words"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(INVALID_TOKEN_MESSAGE));
    }

    @Test
    @DisplayName("permitAll auth endpoint stays reachable without any token")
    void permitAllEndpointIsReachable() throws Exception {
        when(authService.login("alice", "s3cret!x")).thenReturn(new AuthResponse("a.b.c", "r.e.f", 900L));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("a.b.c"));
    }

    @Test
    @DisplayName("a present but unparsable token is rejected with 401 even on a permitAll endpoint")
    void invalidTokenIsRejectedOnPermitAllEndpoint() throws Exception {
        when(jwtService.parse("garbage")).thenThrow(new MalformedJwtException("bad token"));

        mockMvc.perform(post("/api/auth/login")
                        .header("Authorization", BEARER + "garbage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(INVALID_TOKEN_MESSAGE));
    }

    @Test
    @DisplayName("an unparsable token on a protected route answers 401")
    void invalidTokenOnProtectedRouteIsUnauthorized() throws Exception {
        when(jwtService.parse("stale")).thenThrow(new MalformedJwtException("broken"));

        mockMvc.perform(get("/api/words").header("Authorization", BEARER + "stale"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(INVALID_TOKEN_MESSAGE));
    }

    @Test
    @DisplayName("a refresh token presented as Bearer is rejected with 401 (only type=access is accepted)")
    void refreshTokenAsBearerIsRejected() throws Exception {
        when(jwtService.parse("refresh")).thenReturn(payload(TokenType.REFRESH, 1L, "alice"));

        mockMvc.perform(get("/api/words").header("Authorization", BEARER + "refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(INVALID_TOKEN_MESSAGE));
    }

    @Test
    @DisplayName("an access token whose uid no longer matches the stored account is rejected with 401")
    void uidMismatchIsRejected() throws Exception {
        when(jwtService.parse("stale-uid")).thenReturn(payload(TokenType.ACCESS, 2L, "alice"));
        when(userDetailsService.loadUserByUsername("alice"))
                .thenReturn(new UserPrincipal(1L, "alice", "hash"));

        mockMvc.perform(get("/api/words").header("Authorization", BEARER + "stale-uid"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(INVALID_TOKEN_MESSAGE));
    }

    @Test
    @DisplayName("an access token for a deleted account is rejected with 401")
    void deletedAccountIsRejected() throws Exception {
        when(jwtService.parse("gone")).thenReturn(payload(TokenType.ACCESS, 1L, "ghost"));
        when(userDetailsService.loadUserByUsername("ghost"))
                .thenThrow(new UsernameNotFoundException("no account"));

        mockMvc.perform(get("/api/words").header("Authorization", BEARER + "gone"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(INVALID_TOKEN_MESSAGE));
    }

    @Test
    @DisplayName("a valid access token authenticates the request: unmapped /api route answers 404, not 401")
    void validTokenAuthenticatesRequest() throws Exception {
        stubMatchingAccount("good", 1L, "alice");

        mockMvc.perform(get("/api/words").header("Authorization", BEARER + "good"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy tài nguyên"));
    }

    @Test
    @DisplayName("an authenticated request outside /api/** is denied with the contract 403")
    void denyAllRouteIsForbidden() throws Exception {
        stubMatchingAccount("good", 1L, "alice");

        mockMvc.perform(get("/not-api").header("Authorization", BEARER + "good"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Không có quyền truy cập"));
    }

}