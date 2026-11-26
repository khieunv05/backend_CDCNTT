package com.example.english_app_cdcntt.service.tx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.english_app_cdcntt.config.OtpProperties;
import com.example.english_app_cdcntt.entity.PasswordResetCode;
import com.example.english_app_cdcntt.entity.User;
import com.example.english_app_cdcntt.repository.PasswordResetCodeRepository;
import com.example.english_app_cdcntt.repository.RefreshTokenRepository;
import com.example.english_app_cdcntt.repository.UserRepository;
import com.example.english_app_cdcntt.service.OneTimeCodes;
import com.example.english_app_cdcntt.support.TestUsers;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Unit tests for the two write transactions of password recovery. The main regressions guarded
 * here are the in-place refresh of an expired code (delete-then-create breaks UNIQUE(user_id) at
 * flush time) and the fact that a reused password is only reported after the code was accepted.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PasswordResetTxServiceTest {

    private static final Long USER_ID = 7L;
    private static final String RAW_PASSWORD = "raw-secret-password";
    private static final String CURRENT_PASSWORD = "$2a$12$current-password-hash";
    private static final String NEW_PASSWORD = "$2a$12$new-password-hash";
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final OtpProperties OTP = new OtpProperties(Duration.ofMinutes(10), Duration.ofMinutes(15),
            Duration.ofSeconds(60), Duration.ofHours(1), 5, 5);

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetCodeRepository passwordResetCodeRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private OneTimeCodes oneTimeCodes;
    private User user;
    private PasswordResetTxService service;

    @BeforeEach
    void setUp() {
        oneTimeCodes = new OneTimeCodes();
        user = TestUsers.create("carol", "Carol Tran", "carol@example.test", CURRENT_PASSWORD);
        service = new PasswordResetTxService(userRepository, passwordResetCodeRepository,
                refreshTokenRepository, oneTimeCodes, OTP, passwordEncoder, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private PasswordResetCode code(String rawCode, Instant expiresAt) {
        return PasswordResetCode.create(user, oneTimeCodes.hash(rawCode), expiresAt, NOW.minusSeconds(20 * 60));
    }

    // ---------- requestReset ----------

    @Test
    @DisplayName("the first request stores the hash of the mailed code and issues a 15-minute ticket")
    void requestReset_firstRequest_storesHashedCodeAndIssuesTicket() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(passwordResetCodeRepository.findByUser_Id(USER_ID)).thenReturn(Optional.empty());

        PasswordResetRequestResult result = service.requestReset(USER_ID);

        assertThat(result).isInstanceOf(PasswordResetRequestResult.Issued.class);
        VerificationTicket ticket = ((PasswordResetRequestResult.Issued) result).ticket();
        ArgumentCaptor<PasswordResetCode> captor = ArgumentCaptor.forClass(PasswordResetCode.class);
        verify(passwordResetCodeRepository).save(captor.capture());
        assertThat(ticket.email()).isEqualTo("carol@example.test");
        assertThat(ticket.code()).hasSize(6).containsOnlyDigits();
        assertThat(captor.getValue().getCodeHash()).isEqualTo(oneTimeCodes.hash(ticket.code()));
        assertThat(captor.getValue().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        assertThat(captor.getValue().getSendCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("an expired code is refreshed in place: no delete, no insert, hourly quota kept")
    void requestReset_expiredCode_refreshesRowInPlace() {
        PasswordResetCode expired = code("123456", NOW.minusSeconds(60));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(passwordResetCodeRepository.findByUser_Id(USER_ID)).thenReturn(Optional.of(expired));

        PasswordResetRequestResult result = service.requestReset(USER_ID);

        assertThat(result).isInstanceOf(PasswordResetRequestResult.Issued.class);
        verify(passwordResetCodeRepository, never()).delete(any());
        verify(passwordResetCodeRepository).save(expired);
        assertThat(expired.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        assertThat(expired.getSendCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("a request inside the cooldown is suppressed and writes nothing")
    void requestReset_insideCooldown_suppresses() {
        PasswordResetCode fresh = PasswordResetCode.create(user, oneTimeCodes.hash("123456"),
                NOW.plus(Duration.ofMinutes(14)), NOW.minusSeconds(10));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(passwordResetCodeRepository.findByUser_Id(USER_ID)).thenReturn(Optional.of(fresh));

        assertThat(service.requestReset(USER_ID)).isInstanceOf(PasswordResetRequestResult.Suppressed.class);
        verify(passwordResetCodeRepository, never()).save(any());
    }

    @Test
    @DisplayName("the sixth code inside one hour is suppressed even after the previous one expired")
    void requestReset_quotaExceededAcrossTtl_suppresses() {
        PasswordResetCode crowded = code("123456", NOW.minusSeconds(60));
        for (int i = 0; i < 4; i++) {
            crowded.refresh(oneTimeCodes.hash("123456"), NOW.minusSeconds(60), NOW.minusSeconds(20 * 60),
                    Duration.ofHours(1));
        }
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(passwordResetCodeRepository.findByUser_Id(USER_ID)).thenReturn(Optional.of(crowded));

        assertThat(service.requestReset(USER_ID)).isInstanceOf(PasswordResetRequestResult.Suppressed.class);
        assertThat(crowded.getSendCount()).isEqualTo(5);
    }

    // ---------- resetPassword ----------

    @Test
    @DisplayName("a valid code replaces the hash and revokes every session in the same call")
    void resetPassword_correctCode_replacesPasswordAndRevokesSessions() {
        PasswordResetCode resetCode = code("123456", NOW.plus(Duration.ofMinutes(14)));
        when(passwordResetCodeRepository.findByUser_Id(USER_ID)).thenReturn(Optional.of(resetCode));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(RAW_PASSWORD, CURRENT_PASSWORD)).thenReturn(false);
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(NEW_PASSWORD);

        PasswordResetResult result = service.resetPassword(USER_ID, "123456", RAW_PASSWORD);

        assertThat(result).isInstanceOf(PasswordResetResult.Completed.class);
        assertThat(user.getPassword()).isEqualTo(NEW_PASSWORD);
        verify(refreshTokenRepository).deleteByUser_Id(USER_ID);
        verify(passwordResetCodeRepository).delete(resetCode);
    }

    @Test
    @DisplayName("a wrong code counts an attempt and never reveals the password check")
    void resetPassword_wrongCode_countsAttemptAndSkipsPasswordCheck() {
        PasswordResetCode resetCode = code("123456", NOW.plus(Duration.ofMinutes(14)));
        when(passwordResetCodeRepository.findByUser_Id(USER_ID)).thenReturn(Optional.of(resetCode));

        PasswordResetResult result = service.resetPassword(USER_ID, "999999", RAW_PASSWORD);

        assertThat(result).isInstanceOf(PasswordResetResult.InvalidCode.class);
        assertThat(resetCode.getAttempts()).isEqualTo(1);
        verify(userRepository, never()).findById(any());
        verify(refreshTokenRepository, never()).deleteByUser_Id(any());
    }

    @Test
    @DisplayName("the fifth wrong code kills the row so no sixth guess is possible")
    void resetPassword_fifthWrongCode_deletesCode() {
        PasswordResetCode resetCode = code("123456", NOW.plus(Duration.ofMinutes(14)));
        for (int i = 0; i < 4; i++) {
            resetCode.incrementAttempts();
        }
        when(passwordResetCodeRepository.findByUser_Id(USER_ID)).thenReturn(Optional.of(resetCode));

        assertThat(service.resetPassword(USER_ID, "999999", RAW_PASSWORD))
                .isInstanceOf(PasswordResetResult.InvalidCode.class);
        verify(passwordResetCodeRepository).delete(resetCode);
    }

    @Test
    @DisplayName("an expired code is deleted and answers invalid without touching the password")
    void resetPassword_expiredCode_returnsInvalid() {
        PasswordResetCode resetCode = code("123456", NOW.minusSeconds(1));
        when(passwordResetCodeRepository.findByUser_Id(USER_ID)).thenReturn(Optional.of(resetCode));

        assertThat(service.resetPassword(USER_ID, "123456", RAW_PASSWORD))
                .isInstanceOf(PasswordResetResult.InvalidCode.class);
        verify(passwordResetCodeRepository).delete(resetCode);
        verify(userRepository, never()).findById(any());
    }

    @Test
    @DisplayName("a valid code plus the current password answers PasswordReused and changes nothing")
    void resetPassword_reusedPassword_returnsPasswordReused() {
        PasswordResetCode resetCode = code("123456", NOW.plus(Duration.ofMinutes(14)));
        when(passwordResetCodeRepository.findByUser_Id(USER_ID)).thenReturn(Optional.of(resetCode));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(RAW_PASSWORD, CURRENT_PASSWORD)).thenReturn(true);

        PasswordResetResult result = service.resetPassword(USER_ID, "123456", RAW_PASSWORD);

        assertThat(result).isInstanceOf(PasswordResetResult.PasswordReused.class);
        assertThat(user.getPassword()).isEqualTo(CURRENT_PASSWORD);
        verify(passwordEncoder, never()).encode(any());
        verify(refreshTokenRepository, never()).deleteByUser_Id(any());
        verify(passwordResetCodeRepository, never()).delete(any());
    }

    @Test
    @DisplayName("a reset without any stored code answers the generic invalid result")
    void resetPassword_withoutCode_returnsInvalid() {
        when(passwordResetCodeRepository.findByUser_Id(USER_ID)).thenReturn(Optional.empty());

        assertThat(service.resetPassword(USER_ID, "123456", RAW_PASSWORD))
                .isInstanceOf(PasswordResetResult.InvalidCode.class);
    }
}