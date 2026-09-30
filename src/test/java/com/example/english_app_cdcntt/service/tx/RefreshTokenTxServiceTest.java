package com.example.english_app_cdcntt.service.tx;

import com.example.english_app_cdcntt.config.JwtService;
import com.example.english_app_cdcntt.config.TokenType;
import com.example.english_app_cdcntt.dto.AuthResponse;
import com.example.english_app_cdcntt.entity.RefreshToken;
import com.example.english_app_cdcntt.entity.User;
import com.example.english_app_cdcntt.repository.RefreshTokenRepository;
import io.jsonwebtoken.MalformedJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link RefreshTokenTxService}.
 *
 * <p>No Spring context, no database: the repository and JwtService are Mockito mocks and the
 * {@link Clock} is a fixed clock so that expiry decisions are deterministic.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RefreshTokenTxServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    /** Expiry comfortably in the future relative to the fixed clock. */
    private static final Instant FUTURE_EXPIRY = Instant.parse("2026-01-08T00:00:00Z");

    /** Expiry in the past relative to the fixed clock. */
    private static final Instant PAST_EXPIRY = Instant.parse("2025-12-25T00:00:00Z");

    private static final Long USER_ID = 7L;
    private static final String USERNAME = "alice";
    private static final String PRESENTED_TOKEN = "presented-refresh-token";

    private static final String ACCESS_TOKEN = "new-access-token";
    private static final String REFRESH_TOKEN = "new-refresh-token";
    private static final long ACCESS_EXPIRES_IN_SECONDS = 900L;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JwtService jwtService;

    private RefreshTokenTxService service;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenTxService(refreshTokenRepository, jwtService, FIXED_CLOCK);
    }

    // ------------------------------------------------------------------
    // issueSession
    // ------------------------------------------------------------------

    @Test
    @DisplayName("issueSession issues ACCESS then REFRESH, saves the refresh row and returns the AuthResponse")
    void issueSession_issuesAccessThenRefresh_savesRowAndReturnsAuthResponse() {
        // Given
        User user = existingUser();
        JwtService.IssuedToken access = issuedToken(ACCESS_TOKEN, NOW, NOW.plusSeconds(ACCESS_EXPIRES_IN_SECONDS));
        JwtService.IssuedToken refresh = issuedToken(REFRESH_TOKEN, NOW, FUTURE_EXPIRY);

        when(jwtService.issue(TokenType.ACCESS, USER_ID, USERNAME)).thenReturn(access);
        when(jwtService.issue(TokenType.REFRESH, USER_ID, USERNAME)).thenReturn(refresh);
        when(jwtService.accessTokenExpiresInSeconds()).thenReturn(ACCESS_EXPIRES_IN_SECONDS);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        AuthResponse response = service.issueSession(user);

        // Then
        assertThat(response).isEqualTo(new AuthResponse(ACCESS_TOKEN, REFRESH_TOKEN, ACCESS_EXPIRES_IN_SECONDS));

        InOrder inOrder = inOrder(jwtService, refreshTokenRepository);
        inOrder.verify(jwtService).issue(TokenType.ACCESS, USER_ID, USERNAME);
        inOrder.verify(jwtService).issue(TokenType.REFRESH, USER_ID, USERNAME);
        inOrder.verify(refreshTokenRepository).save(any(RefreshToken.class));

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        RefreshToken saved = captor.getValue();
        assertThat(saved.getToken()).isEqualTo(REFRESH_TOKEN);
        assertThat(saved.getExpiryDate()).isEqualTo(FUTURE_EXPIRY);
    }

    @Test
    @DisplayName("issueSession saves a RefreshToken row bound to the same user, token and refresh expiry")
    void issueSession_savesRowWithUserTokenAndExpiry() {
        // Given
        User user = existingUser();
        JwtService.IssuedToken access = issuedToken(ACCESS_TOKEN, NOW, NOW.plusSeconds(ACCESS_EXPIRES_IN_SECONDS));
        JwtService.IssuedToken refresh = issuedToken(REFRESH_TOKEN, NOW, FUTURE_EXPIRY);

        when(jwtService.issue(TokenType.ACCESS, USER_ID, USERNAME)).thenReturn(access);
        when(jwtService.issue(TokenType.REFRESH, USER_ID, USERNAME)).thenReturn(refresh);
        when(jwtService.accessTokenExpiresInSeconds()).thenReturn(ACCESS_EXPIRES_IN_SECONDS);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        service.issueSession(user);

        // Then
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());

        RefreshToken saved = captor.getValue();
        assertThat(saved.getUser()).isSameAs(user);
        assertThat(saved.getUser().getId()).isEqualTo(USER_ID);
        assertThat(saved.getToken()).isEqualTo(REFRESH_TOKEN);
        assertThat(saved.getExpiryDate()).isEqualTo(FUTURE_EXPIRY);
    }

    // ------------------------------------------------------------------
    // attemptRotation — rejection paths
    // ------------------------------------------------------------------

    @Test
    @DisplayName("attemptRotation returns Invalid when no stored row matches the presented token")
    void attemptRotation_whenTokenNotFound_returnsInvalidAndNeverParses() {
        // Given
        when(refreshTokenRepository.findByTokenForUpdate(PRESENTED_TOKEN)).thenReturn(Optional.empty());

        // When
        RefreshRotationResult result = service.attemptRotation(PRESENTED_TOKEN);

        // Then
        assertThat(result).isInstanceOf(RefreshRotationResult.Invalid.class);
        verify(jwtService, never()).parse(anyString());
        verify(refreshTokenRepository, never()).delete(any(RefreshToken.class));
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("attemptRotation returns Expired and deletes the stored row when it has expired")
    void attemptRotation_whenStoredRowExpired_returnsExpiredAndDeletesRow() {
        // Given
        RefreshToken stored = storedRow(existingUser(), PAST_EXPIRY);
        when(refreshTokenRepository.findByTokenForUpdate(PRESENTED_TOKEN)).thenReturn(Optional.of(stored));

        // When
        RefreshRotationResult result = service.attemptRotation(PRESENTED_TOKEN);

        // Then
        assertThat(result).isInstanceOf(RefreshRotationResult.Expired.class);
        verify(refreshTokenRepository).delete(stored);
        verify(jwtService, never()).parse(anyString());
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("attemptRotation returns Invalid when the token cannot be parsed, leaving the row untouched")
    void attemptRotation_whenParseThrows_returnsInvalidAndLeavesRowUntouched() {
        // Given
        RefreshToken stored = storedRow(existingUser(), FUTURE_EXPIRY);
        when(refreshTokenRepository.findByTokenForUpdate(PRESENTED_TOKEN)).thenReturn(Optional.of(stored));
        when(jwtService.parse(PRESENTED_TOKEN)).thenThrow(new MalformedJwtException("malformed token"));

        // When
        RefreshRotationResult result = service.attemptRotation(PRESENTED_TOKEN);

        // Then
        assertThat(result).isInstanceOf(RefreshRotationResult.Invalid.class);
        verify(refreshTokenRepository, never()).delete(any(RefreshToken.class));
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("attemptRotation returns Invalid when the payload token type is ACCESS")
    void attemptRotation_whenPayloadTypeIsAccess_returnsInvalid() {
        // Given
        RefreshToken stored = storedRow(existingUser(), FUTURE_EXPIRY);
        when(refreshTokenRepository.findByTokenForUpdate(PRESENTED_TOKEN)).thenReturn(Optional.of(stored));
        when(jwtService.parse(PRESENTED_TOKEN))
                .thenReturn(payload(USERNAME, USER_ID, TokenType.ACCESS, FUTURE_EXPIRY));

        // When
        RefreshRotationResult result = service.attemptRotation(PRESENTED_TOKEN);

        // Then
        assertThat(result).isInstanceOf(RefreshRotationResult.Invalid.class);
        verify(refreshTokenRepository, never()).delete(any(RefreshToken.class));
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("attemptRotation returns Invalid when the payload username differs from the row user")
    void attemptRotation_whenPayloadUsernameDiffers_returnsInvalid() {
        // Given
        RefreshToken stored = storedRow(existingUser(), FUTURE_EXPIRY);
        when(refreshTokenRepository.findByTokenForUpdate(PRESENTED_TOKEN)).thenReturn(Optional.of(stored));
        when(jwtService.parse(PRESENTED_TOKEN))
                .thenReturn(payload("bob", USER_ID, TokenType.REFRESH, FUTURE_EXPIRY));

        // When
        RefreshRotationResult result = service.attemptRotation(PRESENTED_TOKEN);

        // Then
        assertThat(result).isInstanceOf(RefreshRotationResult.Invalid.class);
        verify(refreshTokenRepository, never()).delete(any(RefreshToken.class));
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("attemptRotation returns Invalid when the payload userId differs from the row user id")
    void attemptRotation_whenPayloadUserIdDiffers_returnsInvalid() {
        // Given
        RefreshToken stored = storedRow(existingUser(), FUTURE_EXPIRY);
        when(refreshTokenRepository.findByTokenForUpdate(PRESENTED_TOKEN)).thenReturn(Optional.of(stored));
        when(jwtService.parse(PRESENTED_TOKEN))
                .thenReturn(payload(USERNAME, 99L, TokenType.REFRESH, FUTURE_EXPIRY));

        // When
        RefreshRotationResult result = service.attemptRotation(PRESENTED_TOKEN);

        // Then
        assertThat(result).isInstanceOf(RefreshRotationResult.Invalid.class);
        verify(refreshTokenRepository, never()).delete(any(RefreshToken.class));
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("attemptRotation returns Invalid when the payload expiry differs from the stored expiry date")
    void attemptRotation_whenPayloadExpiryDiffers_returnsInvalid() {
        // Given
        RefreshToken stored = storedRow(existingUser(), FUTURE_EXPIRY);
        when(refreshTokenRepository.findByTokenForUpdate(PRESENTED_TOKEN)).thenReturn(Optional.of(stored));
        when(jwtService.parse(PRESENTED_TOKEN))
                .thenReturn(payload(USERNAME, USER_ID, TokenType.REFRESH, FUTURE_EXPIRY.plusSeconds(60)));

        // When
        RefreshRotationResult result = service.attemptRotation(PRESENTED_TOKEN);

        // Then
        assertThat(result).isInstanceOf(RefreshRotationResult.Invalid.class);
        verify(refreshTokenRepository, never()).delete(any(RefreshToken.class));
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    // ------------------------------------------------------------------
    // attemptRotation — happy path
    // ------------------------------------------------------------------

    @Test
    @DisplayName("attemptRotation deletes the old row, issues a fresh session and returns Rotated")
    void attemptRotation_whenPayloadMatches_rotatesSession() {
        // Given
        User user = existingUser();
        RefreshToken stored = storedRow(user, FUTURE_EXPIRY);
        when(refreshTokenRepository.findByTokenForUpdate(PRESENTED_TOKEN)).thenReturn(Optional.of(stored));
        when(jwtService.parse(PRESENTED_TOKEN))
                .thenReturn(payload(USERNAME, USER_ID, TokenType.REFRESH, FUTURE_EXPIRY));

        JwtService.IssuedToken access = issuedToken(ACCESS_TOKEN, NOW, NOW.plusSeconds(ACCESS_EXPIRES_IN_SECONDS));
        JwtService.IssuedToken refresh = issuedToken(REFRESH_TOKEN, NOW, FUTURE_EXPIRY);
        when(jwtService.issue(TokenType.ACCESS, USER_ID, USERNAME)).thenReturn(access);
        when(jwtService.issue(TokenType.REFRESH, USER_ID, USERNAME)).thenReturn(refresh);
        when(jwtService.accessTokenExpiresInSeconds()).thenReturn(ACCESS_EXPIRES_IN_SECONDS);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        RefreshRotationResult result = service.attemptRotation(PRESENTED_TOKEN);

        // Then
        assertThat(result).isInstanceOf(RefreshRotationResult.Rotated.class);
        AuthResponse expected = new AuthResponse(ACCESS_TOKEN, REFRESH_TOKEN, ACCESS_EXPIRES_IN_SECONDS);
        assertThat(((RefreshRotationResult.Rotated) result).response()).isEqualTo(expected);

        verify(refreshTokenRepository).delete(stored);
        verify(jwtService).issue(TokenType.ACCESS, USER_ID, USERNAME);
        verify(jwtService).issue(TokenType.REFRESH, USER_ID, USERNAME);
        verify(refreshTokenRepository, times(1)).save(any(RefreshToken.class));
    }

    // ------------------------------------------------------------------
    // revoke
    // ------------------------------------------------------------------

    @Test
    @DisplayName("revoke delegates to deleteByTokenValue with the exact presented token")
    void revoke_delegatesToDeleteByTokenValue() {
        // When
        service.revoke(PRESENTED_TOKEN);

        // Then
        verify(refreshTokenRepository).deleteByTokenValue(PRESENTED_TOKEN);
        verifyNoInteractions(jwtService);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** A persisted-looking user with a non-null id, as required for the payload identity checks. */
    private User existingUser() {
        User user = User.create(USERNAME, "$2a$12$encoded-password-hash");
        ReflectionTestUtils.setField(user, "id", USER_ID);
        return user;
    }

    private RefreshToken storedRow(User user, Instant expiryDate) {
        return RefreshToken.create(user, PRESENTED_TOKEN, expiryDate);
    }

    private JwtService.IssuedToken issuedToken(String token, Instant issuedAt, Instant expiresAt) {
        return new JwtService.IssuedToken(token, issuedAt, expiresAt);
    }

    private JwtService.JwtPayload payload(String username, Long userId, TokenType type, Instant expiresAt) {
        return new JwtService.JwtPayload(username, userId, type, "jti-1", NOW, expiresAt);
    }
}
