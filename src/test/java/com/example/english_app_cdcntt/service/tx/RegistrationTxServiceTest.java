package com.example.english_app_cdcntt.service.tx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.english_app_cdcntt.config.OtpProperties;
import com.example.english_app_cdcntt.entity.PendingRegistration;
import com.example.english_app_cdcntt.entity.User;
import com.example.english_app_cdcntt.exception.DuplicateEmailException;
import com.example.english_app_cdcntt.exception.DuplicateUsernameException;
import com.example.english_app_cdcntt.repository.PendingRegistrationRepository;
import com.example.english_app_cdcntt.repository.UserRepository;
import com.example.english_app_cdcntt.service.OneTimeCodes;
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
 * Unit tests for the two write transactions of registration. The OTP helper is real (its hash is
 * deterministic) so the tests assert the stored hash against the code that was actually mailed,
 * never against a hand-copied digest.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RegistrationTxServiceTest {

    private static final String USERNAME = "alice";
    private static final String DISPLAY_NAME = "Alice Nguyen";
    private static final String EMAIL = "alice@example.test";
    private static final String RAW_PASSWORD = "raw-secret-password";
    private static final String ENCODED_PASSWORD = "$2a$12$encoded-password-hash";
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final OtpProperties OTP = new OtpProperties(Duration.ofMinutes(10), Duration.ofMinutes(15), 5);

    @Mock
    private UserRepository userRepository;

    @Mock
    private PendingRegistrationRepository pendingRegistrationRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private OneTimeCodes oneTimeCodes;
    private RegistrationTxService service;

    @BeforeEach
    void setUp() {
        oneTimeCodes = new OneTimeCodes();
        service = new RegistrationTxService(userRepository, pendingRegistrationRepository, passwordEncoder,
                oneTimeCodes, OTP, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private PendingRegistration draft(String code, Instant expiresAt, int attempts) {
        PendingRegistration pending = PendingRegistration.create(USERNAME, DISPLAY_NAME, EMAIL, ENCODED_PASSWORD,
                oneTimeCodes.hash(code), expiresAt, NOW.minusSeconds(120));
        for (int i = 0; i < attempts; i++) {
            pending.incrementAttempts();
        }
        return pending;
    }

    // ---------- create ----------

    @Test
    @DisplayName("createPendingRegistration stores the BCrypt hash and the hash of the mailed code")
    void createPendingRegistration_freshEmail_storesHashedPasswordAndCode() {
        when(userRepository.existsByUsername(USERNAME)).thenReturn(false);
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(pendingRegistrationRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());
        when(pendingRegistrationRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(ENCODED_PASSWORD);

        VerificationTicket ticket = service.createPendingRegistration(USERNAME, DISPLAY_NAME, EMAIL, RAW_PASSWORD);

        ArgumentCaptor<PendingRegistration> captor = ArgumentCaptor.forClass(PendingRegistration.class);
        verify(pendingRegistrationRepository).save(captor.capture());
        PendingRegistration saved = captor.getValue();

        assertThat(ticket.email()).isEqualTo(EMAIL);
        assertThat(ticket.code()).hasSize(6).containsOnlyDigits();
        assertThat(saved.getPassword()).isEqualTo(ENCODED_PASSWORD).isNotEqualTo(RAW_PASSWORD);
        assertThat(saved.getCodeHash()).isEqualTo(oneTimeCodes.hash(ticket.code()));
        assertThat(saved.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
        assertThat(saved.getAttempts()).isZero();
    }

    @Test
    @DisplayName("createPendingRegistration rejects a username already used by an account")
    void createPendingRegistration_usernameOfAccount_rejects() {
        when(userRepository.existsByUsername(USERNAME)).thenReturn(true);

        assertThatThrownBy(() -> service.createPendingRegistration(USERNAME, DISPLAY_NAME, EMAIL, RAW_PASSWORD))
                .isInstanceOf(DuplicateUsernameException.class);
        verify(pendingRegistrationRepository, never()).save(any());
    }

    @Test
    @DisplayName("createPendingRegistration rejects an email already verified by an account")
    void createPendingRegistration_emailOfAccount_rejects() {
        when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> service.createPendingRegistration(USERNAME, DISPLAY_NAME, EMAIL, RAW_PASSWORD))
                .isInstanceOf(DuplicateEmailException.class);
        verify(pendingRegistrationRepository, never()).save(any());
    }

    @Test
    @DisplayName("createPendingRegistration rejects a username parked by another mailbox's draft")
    void createPendingRegistration_usernameHeldByAnotherDraft_rejects() {
        PendingRegistration other = PendingRegistration.create(USERNAME, "Bob", "bob@example.test", ENCODED_PASSWORD,
                oneTimeCodes.hash("111111"), NOW.plus(Duration.ofMinutes(5)), NOW);
        when(pendingRegistrationRepository.findByUsername(USERNAME)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.createPendingRegistration(USERNAME, DISPLAY_NAME, EMAIL, RAW_PASSWORD))
                .isInstanceOf(DuplicateUsernameException.class);
        verify(pendingRegistrationRepository, never()).save(any());
    }

    @Test
    @DisplayName("an expired draft is refreshed in place: no delete and no insert")
    void createPendingRegistration_expiredDraft_refreshesRowInPlace() {
        PendingRegistration expired = draft("123456", NOW.minusSeconds(1), 0);
        when(pendingRegistrationRepository.findByEmail(EMAIL)).thenReturn(Optional.of(expired));
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(ENCODED_PASSWORD);

        VerificationTicket ticket = service.createPendingRegistration(USERNAME, DISPLAY_NAME, EMAIL, RAW_PASSWORD);

        // delete-then-create would make Hibernate flush INSERT before DELETE and hit UNIQUE(email).
        verify(pendingRegistrationRepository, never()).delete(any());
        verify(pendingRegistrationRepository).save(expired);
        assertThat(expired.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
        assertThat(expired.getCodeHash()).isEqualTo(oneTimeCodes.hash(ticket.code()));
        assertThat(expired.getAttempts()).isZero();
    }

    @Test
    @DisplayName("a second request refreshes the existing draft in place and mails a new code")
    void createPendingRegistration_existingDraft_refreshesInPlace() {
        PendingRegistration fresh = PendingRegistration.create(USERNAME, DISPLAY_NAME, EMAIL, ENCODED_PASSWORD,
                oneTimeCodes.hash("123456"), NOW.plus(Duration.ofMinutes(9)), NOW.minusSeconds(10));
        when(pendingRegistrationRepository.findByEmail(EMAIL)).thenReturn(Optional.of(fresh));
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(ENCODED_PASSWORD);

        VerificationTicket ticket = service.createPendingRegistration(USERNAME, DISPLAY_NAME, EMAIL, RAW_PASSWORD);

        verify(pendingRegistrationRepository, never()).delete(any());
        verify(pendingRegistrationRepository).save(fresh);
        assertThat(fresh.getCodeHash()).isEqualTo(oneTimeCodes.hash(ticket.code()));
        assertThat(fresh.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
        assertThat(fresh.getAttempts()).isZero();
    }

    // ---------- confirm ----------

    @Test
    @DisplayName("confirm with the mailed code deletes the draft and creates the account")
    void confirmRegistration_correctCode_createsAccountAndDeletesDraft() {
        PendingRegistration pending = draft("123456", NOW.plus(Duration.ofMinutes(9)), 0);
        when(pendingRegistrationRepository.findByEmail(EMAIL)).thenReturn(Optional.of(pending));

        RegistrationConfirmationResult result = service.confirmRegistration(EMAIL, "123456");

        assertThat(result).isInstanceOf(RegistrationConfirmationResult.Confirmed.class);
        verify(pendingRegistrationRepository).delete(pending);
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User created = captor.getValue();
        assertThat(created.getUsername()).isEqualTo(USERNAME);
        assertThat(created.getName()).isEqualTo(DISPLAY_NAME);
        assertThat(created.getEmail()).isEqualTo(EMAIL);
        assertThat(created.getPassword()).isEqualTo(ENCODED_PASSWORD);
    }

    @Test
    @DisplayName("a wrong code counts an attempt and keeps the draft")
    void confirmRegistration_wrongCode_countsAttempt() {
        PendingRegistration pending = draft("123456", NOW.plus(Duration.ofMinutes(9)), 0);
        when(pendingRegistrationRepository.findByEmail(EMAIL)).thenReturn(Optional.of(pending));

        RegistrationConfirmationResult result = service.confirmRegistration(EMAIL, "999999");

        assertThat(result).isInstanceOf(RegistrationConfirmationResult.InvalidCode.class);
        assertThat(pending.getAttempts()).isEqualTo(1);
        verify(userRepository, never()).save(any());
        verify(pendingRegistrationRepository, never()).delete(pending);
    }

    @Test
    @DisplayName("the fifth wrong code kills the draft so no sixth guess is possible")
    void confirmRegistration_fifthWrongCode_deletesDraft() {
        PendingRegistration pending = draft("123456", NOW.plus(Duration.ofMinutes(9)), 4);
        when(pendingRegistrationRepository.findByEmail(EMAIL)).thenReturn(Optional.of(pending));

        RegistrationConfirmationResult result = service.confirmRegistration(EMAIL, "999999");

        assertThat(result).isInstanceOf(RegistrationConfirmationResult.InvalidCode.class);
        verify(pendingRegistrationRepository).delete(pending);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("confirm without a draft answers the generic invalid-code result")
    void confirmRegistration_noDraft_returnsInvalidCode() {
        when(pendingRegistrationRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThat(service.confirmRegistration(EMAIL, "123456"))
                .isInstanceOf(RegistrationConfirmationResult.InvalidCode.class);
    }

    @Test
    @DisplayName("confirm after the code expired deletes the draft and answers invalid")
    void confirmRegistration_expiredDraft_returnsInvalidCode() {
        PendingRegistration pending = draft("123456", NOW.minusSeconds(1), 0);
        when(pendingRegistrationRepository.findByEmail(EMAIL)).thenReturn(Optional.of(pending));

        assertThat(service.confirmRegistration(EMAIL, "123456"))
                .isInstanceOf(RegistrationConfirmationResult.InvalidCode.class);
        verify(pendingRegistrationRepository).delete(pending);
        verify(userRepository, never()).save(any());
    }
}
