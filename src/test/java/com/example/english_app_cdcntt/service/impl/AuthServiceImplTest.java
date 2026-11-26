package com.example.english_app_cdcntt.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.english_app_cdcntt.dto.AuthResponse;
import com.example.english_app_cdcntt.entity.User;
import com.example.english_app_cdcntt.exception.InvalidCredentialsException;
import com.example.english_app_cdcntt.exception.PasswordReuseException;
import com.example.english_app_cdcntt.exception.TokenExpiredException;
import com.example.english_app_cdcntt.exception.WrongPasswordException;
import com.example.english_app_cdcntt.repository.UserRepository;
import com.example.english_app_cdcntt.service.tx.PasswordTxService;
import com.example.english_app_cdcntt.service.tx.RefreshRotationResult;
import com.example.english_app_cdcntt.service.tx.RefreshTokenTxService;
import com.example.english_app_cdcntt.support.TestUsers;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * The coordinator half of §5.2: verification order (never write before the password checks pass),
 * the three-way rotation result, and the "throw only after the tx ended" contract. Registration and
 * recovery are not this class (see {@code RegistrationTxServiceTest} and the reset tests).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceImplTest {

    private static final String USERNAME = "alice";
    private static final String CURRENT = "Passw0rd!123";
    private static final String NEXT = "NewPassw0rd!456";
    private static final String ENCODED_CURRENT = "$2a$12$current-hash";
    private static final String ENCODED_NEXT = "$2a$12$next-hash";
    private static final AuthResponse PAIR = new AuthResponse("acc.jwt", "ref.jwt", 900L);

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenTxService refreshTokenTxService;

    @Mock
    private PasswordTxService passwordTxService;

    private AuthServiceImpl service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new AuthServiceImpl(userRepository, passwordEncoder, refreshTokenTxService, passwordTxService);
        user = TestUsers.create(USERNAME, "Alice Nguyen", CURRENT);
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
    }

    // ---------- login ----------

    @Test
    @DisplayName("login verifies the password then returns the pair issued by the tx bean")
    void loginReturnsIssuedPair() {
        when(passwordEncoder.matches(CURRENT, user.getPassword())).thenReturn(true);
        when(refreshTokenTxService.issueSession(user)).thenReturn(PAIR);

        assertThat(service.login(USERNAME, CURRENT)).isEqualTo(PAIR);
        verify(refreshTokenTxService).issueSession(user);
    }

    @Test
    @DisplayName("login with an unknown username answers the same 401 and stores nothing")
    void loginUnknownUserIsRejected() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login("ghost", CURRENT))
                .isInstanceOf(InvalidCredentialsException.class);
        verifyNoInteractions(refreshTokenTxService);
    }

    @Test
    @DisplayName("login with a wrong password answers 401 before any token is written")
    void loginWrongPasswordIsRejected() {
        when(passwordEncoder.matches("wrong", user.getPassword())).thenReturn(false);

        assertThatThrownBy(() -> service.login(USERNAME, "wrong"))
                .isInstanceOf(InvalidCredentialsException.class);
        verifyNoInteractions(refreshTokenTxService);
    }

    // ---------- refresh ----------

    @Test
    @DisplayName("refresh returns the rotated pair")
    void refreshReturnsRotatedPair() {
        when(refreshTokenTxService.attemptRotation("ref.jwt"))
                .thenReturn(new RefreshRotationResult.Rotated(PAIR));

        assertThat(service.refresh("ref.jwt")).isEqualTo(PAIR);
    }

    @Test
    @DisplayName("an expired refresh token answers 401 Phiên đăng nhập hết hạn")
    void refreshExpiredAnswersTokenExpired() {
        when(refreshTokenTxService.attemptRotation("old"))
                .thenReturn(new RefreshRotationResult.Expired());

        assertThatThrownBy(() -> service.refresh("old"))
                .isInstanceOf(TokenExpiredException.class);
    }

    @Test
    @DisplayName("an unknown or tampered refresh token answers the generic 401")
    void refreshInvalidAnswersInvalidCredentials() {
        when(refreshTokenTxService.attemptRotation("nope"))
                .thenReturn(new RefreshRotationResult.Invalid());

        assertThatThrownBy(() -> service.refresh("nope"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    // ---------- logout ----------

    @Test
    @DisplayName("logout delegates the exact token string to the revoke transaction")
    void logoutDelegatesRevoke() {
        service.logout("ref.jwt");

        verify(refreshTokenTxService).revoke("ref.jwt");
    }

    // ---------- change-password ----------

    @Test
    @DisplayName("change-password encodes the new secret and revokes sessions only after both checks")
    void changePasswordUpdatesHashAndRevokes() {
        when(passwordEncoder.matches(CURRENT, user.getPassword())).thenReturn(true);
        when(passwordEncoder.matches(NEXT, user.getPassword())).thenReturn(false);
        when(passwordEncoder.encode(NEXT)).thenReturn(ENCODED_NEXT);

        service.changePassword(USERNAME, CURRENT, NEXT);

        verify(passwordTxService).changePasswordAndRevokeSessions(any(), eq(ENCODED_NEXT));
    }

    @Test
    @DisplayName("a wrong current password answers 400 and touches no hash")
    void changePasswordWrongCurrentIsRejected() {
        when(passwordEncoder.matches("wrong", user.getPassword())).thenReturn(false);

        assertThatThrownBy(() -> service.changePassword(USERNAME, "wrong", NEXT))
                .isInstanceOf(WrongPasswordException.class);
        verifyNoInteractions(passwordTxService);
    }

    @Test
    @DisplayName("the same password answers 400 instead of re-hashing it")
    void changePasswordReuseIsRejected() {
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        assertThatThrownBy(() -> service.changePassword(USERNAME, NEXT, NEXT))
                .isInstanceOf(PasswordReuseException.class);
        verifyNoInteractions(passwordTxService);
    }

    @Test
    @DisplayName("an unknown account answers 401 rather than leaking that the username is gone")
    void changePasswordUnknownUserIsRejected() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changePassword("ghost", CURRENT, NEXT))
                .isInstanceOf(InvalidCredentialsException.class);
        verifyNoInteractions(passwordTxService);
    }

    @Test
    @DisplayName("login never returns a pair when the encoder rejects the secret")
    void loginNeverIssuesOnPasswordMismatch() {
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> service.login(USERNAME, CURRENT))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(refreshTokenTxService, never()).issueSession(any());
    }
}
