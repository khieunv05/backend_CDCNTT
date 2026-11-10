package com.example.english_app_cdcntt.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.english_app_cdcntt.config.AppUserDetailsService;
import com.example.english_app_cdcntt.config.JwtService;
import com.example.english_app_cdcntt.config.SecurityConfig;
import com.example.english_app_cdcntt.config.TokenType;
import com.example.english_app_cdcntt.config.UserPrincipal;
import com.example.english_app_cdcntt.dto.UserProfileDto;
import com.example.english_app_cdcntt.exception.GlobalExceptionHandler;
import com.example.english_app_cdcntt.service.UserService;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * MVC + security slice for {@code /api/users/me}: the profile routes sit under {@code /api/**}, so
 * they must reject anonymous callers and must take the account identity from the verified token,
 * never from the body. Also covers the validation contract of {@code UpdateNameForm}.
 */
@WebMvcTest(controllers = UserController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class UserControllerSecurityTest {

    private static final String BEARER = "Bearer ";
    private static final String INVALID_TOKEN_MESSAGE = "Token không hợp lệ hoặc hết hạn";
    private static final String GOOD_TOKEN = "good";
    private static final String PROFILE_BODY = "{\"name\":\"Alice mới\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AppUserDetailsService userDetailsService;

    private static JwtService.JwtPayload payload(TokenType type, Long userId, String username) {
        return new JwtService.JwtPayload(username, userId, type, "jti-1",
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:30:00Z"));
    }

    private void stubMatchingAccount() {
        when(jwtService.parse(GOOD_TOKEN)).thenReturn(payload(TokenType.ACCESS, 7L, "alice"));
        when(userDetailsService.loadUserByUsername("alice"))
                .thenReturn(new UserPrincipal(7L, "alice", "hash"));
    }

    @Test
    @DisplayName("GET /api/users/me without a Bearer token answers the entry point 401")
    void getProfileWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(INVALID_TOKEN_MESSAGE));
    }

    @Test
    @DisplayName("PUT /api/users/me without a Bearer token answers the entry point 401")
    void updateNameWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(put("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PROFILE_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(INVALID_TOKEN_MESSAGE));
    }

    @Test
    @DisplayName("GET /api/users/me returns the bare profile of the token owner")
    void getProfileReturnsBareDto() throws Exception {
        stubMatchingAccount();
        when(userService.getProfile("alice"))
                .thenReturn(new UserProfileDto(7L, "alice", "Alice Nguyen"));

        mockMvc.perform(get("/api/users/me").header("Authorization", BEARER + GOOD_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.name").value("Alice Nguyen"))
                .andExpect(jsonPath("$.message").doesNotExist());
    }

    @Test
    @DisplayName("PUT /api/users/me returns the success envelope with the updated profile")
    void updateNameReturnsEnvelope() throws Exception {
        stubMatchingAccount();
        when(userService.updateName("alice", "Alice mới"))
                .thenReturn(new UserProfileDto(7L, "alice", "Alice mới"));

        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", BEARER + GOOD_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PROFILE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Cập nhật tên thành công"))
                .andExpect(jsonPath("$.data.id").value(7))
                .andExpect(jsonPath("$.data.name").value("Alice mới"));

        verify(userService).updateName("alice", "Alice mới");
    }

    @Test
    @DisplayName("PUT /api/users/me rejects a blank name with the update-name validation message")
    void updateNameRejectsBlankName() throws Exception {
        stubMatchingAccount();

        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", BEARER + GOOD_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("name"));
    }

    @Test
    @DisplayName("PUT /api/users/me rejects a name longer than 100 characters")
    void updateNameRejectsTooLongName() throws Exception {
        stubMatchingAccount();
        String tooLong = "a".repeat(101);

        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", BEARER + GOOD_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("name"));
    }
}
