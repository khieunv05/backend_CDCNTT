package com.example.english_app_cdcntt.service.impl;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.english_app_cdcntt.entity.PendingRegistration;
import com.example.english_app_cdcntt.exception.DuplicateEmailException;
import com.example.english_app_cdcntt.exception.DuplicateUsernameException;
import com.example.english_app_cdcntt.exception.InvalidVerificationCodeException;
import com.example.english_app_cdcntt.repository.PendingRegistrationRepository;
import com.example.english_app_cdcntt.repository.UserRepository;
import com.example.english_app_cdcntt.service.EmailPurpose;
import com.example.english_app_cdcntt.service.EmailSender;
import com.example.english_app_cdcntt.service.tx.RegistrationConfirmationResult;
import com.example.english_app_cdcntt.service.tx.RegistrationTxService;
import com.example.english_app_cdcntt.service.tx.VerificationTicket;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceImplTest {

    private static final VerificationTicket TICKET =
            new VerificationTicket("alice@example.test", "123456");

    @Mock private UserRepository userRepository;
    @Mock private PendingRegistrationRepository pendingRegistrationRepository;
    @Mock private RegistrationTxService registrationTxService;
    @Mock private EmailSender emailSender;
    @InjectMocks private RegistrationServiceImpl service;

    @Test
    @DisplayName("a fresh email stores the draft first and only then sends the code")
    void requestRegistration_freshEmail_storesDraftThenSendsMail() {
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.test")).thenReturn(false);
        when(pendingRegistrationRepository.findByUsername("alice")).thenReturn(Optional.empty());
        when(registrationTxService.createPendingRegistration("alice", "Alice Nguyen", "alice@example.test",
                "Passw0rd!123")).thenReturn(TICKET);

        service.requestRegistration("alice", " Alice Nguyen ", " Alice@Example.Test ", "Passw0rd!123");

        verify(registrationTxService).createPendingRegistration("alice", "Alice Nguyen", "alice@example.test",
                "Passw0rd!123");
        verify(emailSender).sendVerificationCode("alice@example.test", "123456", EmailPurpose.REGISTRATION);
    }

    @Test
    @DisplayName("a username already used by an account fails before any draft or mail work")
    void requestRegistration_usernameTaken_throwsConflict() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> service.requestRegistration("alice", "Alice", "alice@example.test", "Passw0rd!123"))
                .isInstanceOf(DuplicateUsernameException.class);

        verifyNoInteractions(registrationTxService, emailSender);
    }

    @Test
    @DisplayName("an email already verified fails before any draft or mail work")
    void requestRegistration_emailTaken_throwsConflict() {
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.test")).thenReturn(true);

        assertThatThrownBy(() -> service.requestRegistration("alice", "Alice", "alice@example.test", "Passw0rd!123"))
                .isInstanceOf(DuplicateEmailException.class);

        verifyNoInteractions(registrationTxService, emailSender);
    }

    @Test
    @DisplayName("a draft username held by another mailbox answers 409 immediately")
    void requestRegistration_draftUsernameHeldByOtherMailbox_throwsConflict() {
        PendingRegistration draft = mock(PendingRegistration.class);
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.test")).thenReturn(false);
        when(pendingRegistrationRepository.findByUsername("alice")).thenReturn(Optional.of(draft));
        when(draft.getEmail()).thenReturn("other@example.test");

        assertThatThrownBy(() -> service.requestRegistration("alice", "Alice", "alice@example.test", "Passw0rd!123"))
                .isInstanceOf(DuplicateUsernameException.class);

        verifyNoInteractions(registrationTxService, emailSender);
    }

    @Test
    @DisplayName("a draft username held by the same mailbox may be refreshed")
    void requestRegistration_draftUsernameHeldBySameMailbox_refreshesDraft() {
        PendingRegistration draft = mock(PendingRegistration.class);
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.test")).thenReturn(false);
        when(pendingRegistrationRepository.findByUsername("alice")).thenReturn(Optional.of(draft));
        when(draft.getEmail()).thenReturn("alice@example.test");
        when(registrationTxService.createPendingRegistration("alice", "Alice", "alice@example.test",
                "Passw0rd!123")).thenReturn(TICKET);

        service.requestRegistration("alice", "Alice", "alice@example.test", "Passw0rd!123");

        verify(registrationTxService).createPendingRegistration("alice", "Alice", "alice@example.test",
                "Passw0rd!123");
    }

    @Test
    @DisplayName("a confirmed code just returns; there is nothing left to map")
    void confirmRegistration_confirmed_returnsNormally() {
        when(registrationTxService.confirmRegistration("alice@example.test", "123456"))
                .thenReturn(new RegistrationConfirmationResult.Confirmed());

        assertThatCode(() -> service.confirmRegistration(" Alice@Example.Test ", "123456"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a wrong, expired or missing code shares one 400 exception")
    void confirmRegistration_invalidCode_throwsInvalidCode() {
        when(registrationTxService.confirmRegistration("alice@example.test", "999999"))
                .thenReturn(new RegistrationConfirmationResult.InvalidCode());

        assertThatThrownBy(() -> service.confirmRegistration("alice@example.test", "999999"))
                .isInstanceOf(InvalidVerificationCodeException.class);
    }

    @Test
    @DisplayName("a username unique violation during confirm is mapped to 409")
    void confirmRegistration_usernameConstraint_throwsDuplicateUsername() {
        when(registrationTxService.confirmRegistration("alice@example.test", "123456"))
                .thenThrow(constraint("uk_users_username"));

        assertThatThrownBy(() -> service.confirmRegistration("alice@example.test", "123456"))
                .isInstanceOf(DuplicateUsernameException.class);
    }

    @Test
    @DisplayName("an email unique violation during confirm is mapped to 409")
    void confirmRegistration_emailConstraint_throwsDuplicateEmail() {
        when(registrationTxService.confirmRegistration("alice@example.test", "123456"))
                .thenThrow(constraint("uk_users_email"));

        assertThatThrownBy(() -> service.confirmRegistration("alice@example.test", "123456"))
                .isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    @DisplayName("an unrelated integrity violation is not hidden behind a 409")
    void confirmRegistration_otherConstraint_rethrows() {
        DataIntegrityViolationException failure = constraint("some_other_key");
        when(registrationTxService.confirmRegistration("alice@example.test", "123456")).thenThrow(failure);

        assertThatThrownBy(() -> service.confirmRegistration("alice@example.test", "123456"))
                .isSameAs(failure);
    }

    @Test
    @DisplayName("a concurrent duplicate email on the draft table is mapped to 409")
    void requestRegistration_concurrentDraftEmail_throwsDuplicateEmail() {
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.test")).thenReturn(false);
        when(pendingRegistrationRepository.findByUsername("alice")).thenReturn(Optional.empty());
        when(registrationTxService.createPendingRegistration(any(), any(), any(), any()))
                .thenThrow(constraint("uk_pending_registrations_email"));

        assertThatThrownBy(() -> service.requestRegistration("alice", "Alice", "alice@example.test", "Passw0rd!123"))
                .isInstanceOf(DuplicateEmailException.class);
        verifyNoInteractions(emailSender);
    }

    @Test
    @DisplayName("a concurrent duplicate username on the draft table is mapped to 409")
    void requestRegistration_concurrentDraftUsername_throwsDuplicateUsername() {
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.test")).thenReturn(false);
        when(pendingRegistrationRepository.findByUsername("alice")).thenReturn(Optional.empty());
        when(registrationTxService.createPendingRegistration(any(), any(), any(), any()))
                .thenThrow(constraint("uk_pending_registrations_username"));

        assertThatThrownBy(() -> service.requestRegistration("alice", "Alice", "alice@example.test", "Passw0rd!123"))
                .isInstanceOf(DuplicateUsernameException.class);
        verifyNoInteractions(emailSender);
    }

    private static DataIntegrityViolationException constraint(String name) {
        return new DataIntegrityViolationException("Duplicate entry for key '" + name + "'",
                new SQLIntegrityConstraintViolationException("Duplicate entry for key '" + name + "'"));
    }
}