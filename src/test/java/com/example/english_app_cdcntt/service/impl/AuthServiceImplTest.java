package com.example.english_app_cdcntt.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.english_app_cdcntt.dto.AuthResponse;
import com.example.english_app_cdcntt.entity.User;
import com.example.english_app_cdcntt.exception.DuplicateUsernameException;
import com.example.english_app_cdcntt.exception.InvalidCredentialsException;
import com.example.english_app_cdcntt.exception.TokenExpiredException;
import com.example.english_app_cdcntt.repository.UserRepository;
import com.example.english_app_cdcntt.service.tx.RefreshRotationResult;
import com.example.english_app_cdcntt.service.tx.RefreshTokenTxService;
import com.example.english_app_cdcntt.service.tx.RegistrationTxService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceImplTest {

    private static final String DUPLICATE_USERNAME_MESSAGE = "Tên đăng nhập đã tồn tại";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RegistrationTxService registrationTxService;

    @Mock
    private RefreshTokenTxService refreshTokenTxService;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(userRepository, passwordEncoder, registrationTxService, refreshTokenTxService);
    }

    // ---------------------------------------------------------------- register

    @Test
    @DisplayName("register chuyển tiếp chính xác username và mật khẩu thô tới createUser")
    void register_delegatesToCreateUserWithExactArguments() {
        authService.register("alice", "s3cret!");

        verify(registrationTxService).createUser("alice", "s3cret!");
        verifyNoInteractions(userRepository, passwordEncoder, refreshTokenTxService);
    }

    @Test
    @DisplayName("register không ném ngoại lệ khi createUser thành công")
    void register_succeedsWhenCreateUserDoesNothing() {
        assertThatCode(() -> authService.register("alice", "s3cret!"))
                .doesNotThrowAnyException();

        verify(registrationTxService).createUser("alice", "s3cret!");
    }

    @Test
    @DisplayName("register ánh xạ DataIntegrityViolationException có nguyên nhân uk_users_username thành DuplicateUsernameException")
    void register_mapsDirectSqlConstraintViolationToDuplicateUsername() {
        SQLIntegrityConstraintViolationException sqlException = new SQLIntegrityConstraintViolationException(
                "Duplicate entry 'alice' for key 'uk_users_username'");
        DataIntegrityViolationException original = new DataIntegrityViolationException("insert failed", sqlException);
        doThrow(original).when(registrationTxService).createUser(anyString(), anyString());

        assertThatThrownBy(() -> authService.register("alice", "s3cret!"))
                .isInstanceOf(DuplicateUsernameException.class)
                .hasMessage(DUPLICATE_USERNAME_MESSAGE)
                .satisfies(thrown -> assertThat(thrown).isNotSameAs(original));
    }

    @Test
    @DisplayName("register ánh xạ chuỗi nguyên nhân sâu hơn chứa uk_users_username thành DuplicateUsernameException")
    void register_mapsDeeperCauseChainToDuplicateUsername() {
        SQLIntegrityConstraintViolationException sqlException = new SQLIntegrityConstraintViolationException(
                "Duplicate entry 'alice' for key 'uk_users_username'");
        RuntimeException middle = new RuntimeException("wrapped", sqlException);
        DataIntegrityViolationException original = new DataIntegrityViolationException("insert failed", middle);
        doThrow(original).when(registrationTxService).createUser(anyString(), anyString());

        assertThatThrownBy(() -> authService.register("alice", "s3cret!"))
                .isInstanceOf(DuplicateUsernameException.class)
                .hasMessage(DUPLICATE_USERNAME_MESSAGE);
    }

    @Test
    @DisplayName("register ném lại DataIntegrityViolationException gốc khi vi phạm khoá khác không phải uk_users_username")
    void register_rethrowsOriginalWhenConstraintNameDiffers() {
        SQLIntegrityConstraintViolationException sqlException = new SQLIntegrityConstraintViolationException(
                "Duplicate entry 'x' for key 'uk_users_email'");
        DataIntegrityViolationException original = new DataIntegrityViolationException("insert failed", sqlException);
        doThrow(original).when(registrationTxService).createUser(anyString(), anyString());

        assertThatThrownBy(() -> authService.register("alice", "s3cret!"))
                .isSameAs(original)
                .isNotInstanceOf(DuplicateUsernameException.class);
    }

    @Test
    @DisplayName("register ném lại DataIntegrityViolationException gốc khi chuỗi nguyên nhân không có SQLIntegrityConstraintViolationException")
    void register_rethrowsOriginalWhenNoSqlConstraintViolationInChain() {
        DataIntegrityViolationException original = new DataIntegrityViolationException("insert failed",
                new RuntimeException("boom"));
        doThrow(original).when(registrationTxService).createUser(anyString(), anyString());

        assertThatThrownBy(() -> authService.register("alice", "s3cret!"))
                .isSameAs(original)
                .isNotInstanceOf(DuplicateUsernameException.class);
    }

    // ------------------------------------------------------------------- login

    @Test
    @DisplayName("login trả về đúng AuthResponse từ issueSession khi mật khẩu khớp")
    void login_returnsSessionIssuedByRefreshTokenService() {
        User user = User.create("alice", "hash");
        AuthResponse expected = new AuthResponse("access-token", "refresh-token", 3600L);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("s3cret!", "hash")).thenReturn(true);
        when(refreshTokenTxService.issueSession(user)).thenReturn(expected);

        AuthResponse actual = authService.login("alice", "s3cret!");

        assertThat(actual).isSameAs(expected);
        assertThat(actual).isEqualTo(new AuthResponse("access-token", "refresh-token", 3600L));
        verify(userRepository).findByUsername("alice");
        verify(passwordEncoder).matches("s3cret!", "hash");
        verify(refreshTokenTxService).issueSession(user);
    }

    @Test
    @DisplayName("login ném InvalidCredentialsException khi không tìm thấy người dùng")
    void login_throwsInvalidCredentialsWhenUserNotFound() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login("ghost", "s3cret!"))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(refreshTokenTxService, never()).issueSession(any());
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    @DisplayName("login ném InvalidCredentialsException khi mật khẩu không khớp")
    void login_throwsInvalidCredentialsWhenPasswordDoesNotMatch() {
        User user = User.create("alice", "hash");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login("alice", "wrong"))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(refreshTokenTxService, never()).issueSession(any());
    }

    // ----------------------------------------------------------------- refresh

    @Test
    @DisplayName("refresh trả về response của kết quả Rotated")
    void refresh_returnsResponseForRotatedResult() {
        AuthResponse expected = new AuthResponse("new-access", "new-refresh", 7200L);
        when(refreshTokenTxService.attemptRotation("presented-token"))
                .thenReturn(new RefreshRotationResult.Rotated(expected));

        AuthResponse actual = authService.refresh("presented-token");

        assertThat(actual).isSameAs(expected);
        assertThat(actual).isEqualTo(new AuthResponse("new-access", "new-refresh", 7200L));
        verify(refreshTokenTxService).attemptRotation("presented-token");
    }

    @Test
    @DisplayName("refresh ném TokenExpiredException khi kết quả là Expired")
    void refresh_throwsTokenExpiredForExpiredResult() {
        when(refreshTokenTxService.attemptRotation("expired-token"))
                .thenReturn(new RefreshRotationResult.Expired());

        assertThatThrownBy(() -> authService.refresh("expired-token"))
                .isInstanceOf(TokenExpiredException.class);
    }

    @Test
    @DisplayName("refresh ném InvalidCredentialsException khi kết quả là Invalid")
    void refresh_throwsInvalidCredentialsForInvalidResult() {
        when(refreshTokenTxService.attemptRotation("invalid-token"))
                .thenReturn(new RefreshRotationResult.Invalid());

        assertThatThrownBy(() -> authService.refresh("invalid-token"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    // ------------------------------------------------------------------ logout

    @Test
    @DisplayName("logout chuyển tiếp chính xác token tới revoke")
    void logout_delegatesToRevokeWithExactToken() {
        authService.logout("presented-token");

        verify(refreshTokenTxService).revoke("presented-token");
        verifyNoInteractions(userRepository, passwordEncoder, registrationTxService);
    }
}
