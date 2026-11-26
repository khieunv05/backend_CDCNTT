package com.example.english_app_cdcntt.service.impl;

import com.example.english_app_cdcntt.entity.User;
import com.example.english_app_cdcntt.exception.EmailDeliveryException;
import com.example.english_app_cdcntt.exception.InvalidVerificationCodeException;
import com.example.english_app_cdcntt.exception.PasswordReuseException;
import com.example.english_app_cdcntt.repository.UserRepository;
import com.example.english_app_cdcntt.service.EmailAddresses;
import com.example.english_app_cdcntt.service.EmailPurpose;
import com.example.english_app_cdcntt.service.EmailSender;
import com.example.english_app_cdcntt.service.PasswordResetService;
import com.example.english_app_cdcntt.service.tx.PasswordResetRequestResult;
import com.example.english_app_cdcntt.service.tx.PasswordResetResult;
import com.example.english_app_cdcntt.service.tx.PasswordResetTxService;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Coordinator of the two recovery endpoints. Both are enumeration-safe: request always reports
 * success (unknown email, cooldown, quota and even a broken mail provider), and reset answers the
 * same 400 for "no such email" as for a wrong code.
 */
@Service
public class PasswordResetServiceImpl implements PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetServiceImpl.class);

    private final UserRepository userRepository;
    private final PasswordResetTxService passwordResetTxService;
    private final EmailSender emailSender;

    public PasswordResetServiceImpl(UserRepository userRepository, PasswordResetTxService passwordResetTxService,
            EmailSender emailSender) {
        this.userRepository = userRepository;
        this.passwordResetTxService = passwordResetTxService;
        this.emailSender = emailSender;
    }

    @Override
    public void requestReset(String email) {
        Optional<User> account = userRepository.findByEmail(EmailAddresses.normalize(email));
        if (account.isEmpty()) {
            return;
        }
        PasswordResetRequestResult result = passwordResetTxService.requestReset(account.get().getId());
        if (result instanceof PasswordResetRequestResult.Issued issued) {
            try {
                emailSender.sendVerificationCode(issued.ticket().email(), issued.ticket().code(),
                        EmailPurpose.PASSWORD_RESET);
            } catch (EmailDeliveryException e) {
                // §5.2 — answering 502 here would confirm the email exists, so the failure stays a log.
                log.warn("Không gửi được mã đặt lại mật khẩu (userId={})", account.get().getId(), e);
            }
        }
    }

    @Override
    public void resetPassword(String email, String code, String newPassword) {
        User user = userRepository.findByEmail(EmailAddresses.normalize(email))
                .orElseThrow(InvalidVerificationCodeException::new);
        PasswordResetResult result = passwordResetTxService.resetPassword(user.getId(), code, newPassword);
        if (result instanceof PasswordResetResult.InvalidCode) {
            throw new InvalidVerificationCodeException();
        }
        if (result instanceof PasswordResetResult.PasswordReused) {
            throw new PasswordReuseException();
        }
    }
}
