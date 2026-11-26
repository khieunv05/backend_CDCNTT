package com.example.english_app_cdcntt.service.impl;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.english_app_cdcntt.entity.User;
import com.example.english_app_cdcntt.exception.EmailDeliveryException;
import com.example.english_app_cdcntt.exception.InvalidVerificationCodeException;
import com.example.english_app_cdcntt.exception.PasswordReuseException;
import com.example.english_app_cdcntt.repository.UserRepository;
import com.example.english_app_cdcntt.service.EmailPurpose;
import com.example.english_app_cdcntt.service.EmailSender;
import com.example.english_app_cdcntt.service.tx.PasswordResetRequestResult;
import com.example.english_app_cdcntt.service.tx.PasswordResetResult;
import com.example.english_app_cdcntt.service.tx.PasswordResetTxService;
import com.example.english_app_cdcntt.service.tx.VerificationTicket;
import com.example.english_app_cdcntt.support.TestUsers;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceImplTest {

    private static final User ACCOUNT =
            TestUsers.create("alice", "Alice Nguyen", "alice@example.test", "Passw0rd!123");
    private static final VerificationTicket TICKET =
            new VerificationTicket("alice@example.test", "654321");

    @Mock private UserRepository userRepository;
    @Mock private PasswordResetTxService passwordResetTxService;
    @Mock private EmailSender emailSender;
    @InjectMocks private PasswordResetServiceImpl service;

    @Test
    @DisplayName("an unknown email answers nothing: no code, no mail, no exception")
    void requestReset_unknownEmail_doesNothing() {
        when(userRepository.findByEmail("ghost@example.test")).thenReturn(Optional.empty());

        service.requestReset(" Ghost@Example.Test ");

        verify(userRepository).findByEmail("ghost@example.test");
        verifyNoInteractions(passwordResetTxService, emailSender);
    }

    @Test
    @DisplayName("an issued code is mailed to the account address with the reset purpose")
    void requestReset_issued_sendsResetMail() {
        when(userRepository.findByEmail("alice@example.test")).thenReturn(Optional.of(ACCOUNT));
        when(passwordResetTxService.requestReset(any()))
                .thenReturn(new PasswordResetRequestResult.Issued(TICKET));

        service.requestReset("Alice@Example.Test");

        verify(emailSender).sendVerificationCode("alice@example.test", "654321", EmailPurpose.PASSWORD_RESET);
    }

    @Test
    @DisplayName("a suppressed request (cooldown or quota) sends no mail")
    void requestReset_suppressed_sendsNothing() {
        when(userRepository.findByEmail("alice@example.test")).thenReturn(Optional.of(ACCOUNT));
        when(passwordResetTxService.requestReset(any()))
                .thenReturn(new PasswordResetRequestResult.Suppressed());

        service.requestReset("alice@example.test");

        verifyNoInteractions(emailSender);
    }

    @Test
    @DisplayName("a broken mail provider must not turn forgot-password into a 502")
    void requestReset_mailFailure_isSwallowed() {
        when(userRepository.findByEmail("alice@example.test")).thenReturn(Optional.of(ACCOUNT));
        when(passwordResetTxService.requestReset(any()))
                .thenReturn(new PasswordResetRequestResult.Issued(TICKET));
        doThrow(new EmailDeliveryException(new IllegalStateException("smtp down"))).when(emailSender)
                .sendVerificationCode(any(), any(), any());

        assertThatCode(() -> service.requestReset("alice@example.test")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("reset with a wrong or expired code answers the shared 400 exception")
    void resetPassword_invalidCode_throwsInvalidCode() {
        when(userRepository.findByEmail("alice@example.test")).thenReturn(Optional.of(ACCOUNT));
        when(passwordResetTxService.resetPassword(any(), any(), any()))
                .thenReturn(new PasswordResetResult.InvalidCode());

        assertThatThrownBy(() -> service.resetPassword("alice@example.test", "999999", "NewPassw0rd!456"))
                .isInstanceOf(InvalidVerificationCodeException.class);
    }

    @Test
    @DisplayName("reusing the current password answers 400")
    void resetPassword_reused_throwsPasswordReuse() {
        when(userRepository.findByEmail("alice@example.test")).thenReturn(Optional.of(ACCOUNT));
        when(passwordResetTxService.resetPassword(any(), any(), any()))
                .thenReturn(new PasswordResetResult.PasswordReused());

        assertThatThrownBy(() -> service.resetPassword("alice@example.test", "654321", "Passw0rd!123"))
                .isInstanceOf(PasswordReuseException.class);
    }

    @Test
    @DisplayName("a completed reset returns normally and issues no token")
    void resetPassword_completed_returnsNormally() {
        when(userRepository.findByEmail("alice@example.test")).thenReturn(Optional.of(ACCOUNT));
        when(passwordResetTxService.resetPassword(any(), any(), any()))
                .thenReturn(new PasswordResetResult.Completed());

        assertThatCode(() -> service.resetPassword("alice@example.test", "654321", "NewPassw0rd!456"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a reset for an unknown email answers the same 400 as a wrong code")
    void resetPassword_unknownEmail_throwsInvalidCode() {
        when(userRepository.findByEmail("ghost@example.test")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resetPassword("ghost@example.test", "654321", "NewPassw0rd!456"))
                .isInstanceOf(InvalidVerificationCodeException.class);

        verifyNoInteractions(passwordResetTxService);
    }
}