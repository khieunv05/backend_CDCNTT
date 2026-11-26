package com.example.english_app_cdcntt.service.impl;

import com.example.english_app_cdcntt.exception.DuplicateEmailException;
import com.example.english_app_cdcntt.exception.DuplicateUsernameException;
import com.example.english_app_cdcntt.exception.InvalidVerificationCodeException;
import com.example.english_app_cdcntt.repository.PendingRegistrationRepository;
import com.example.english_app_cdcntt.repository.UserRepository;
import com.example.english_app_cdcntt.service.EmailAddresses;
import com.example.english_app_cdcntt.service.EmailPurpose;
import com.example.english_app_cdcntt.service.EmailSender;
import com.example.english_app_cdcntt.service.RegistrationService;
import com.example.english_app_cdcntt.service.tx.RegistrationConfirmationResult;
import com.example.english_app_cdcntt.service.tx.RegistrationTxService;
import com.example.english_app_cdcntt.service.tx.VerificationTicket;
import java.sql.SQLIntegrityConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Coordinator of the two registration endpoints (§5.2:234). Deliberately NOT transactional: the
 * draft is committed by {@link RegistrationTxService}, then the mail leaves, so a slow SMTP server
 * never holds a database transaction — and a failed send leaves a committed draft the user can
 * retry.
 */
@Service
public class RegistrationServiceImpl implements RegistrationService {

    /** Constraint names from V4 — the only integrity violations mapped to 409. */
    private static final String USERNAME_UNIQUE_CONSTRAINT = "uk_users_username";
    private static final String EMAIL_UNIQUE_CONSTRAINT = "uk_users_email";
    private static final String PENDING_USERNAME_UNIQUE_CONSTRAINT = "uk_pending_registrations_username";
    private static final String PENDING_EMAIL_UNIQUE_CONSTRAINT = "uk_pending_registrations_email";

    private final UserRepository userRepository;
    private final PendingRegistrationRepository pendingRegistrationRepository;
    private final RegistrationTxService registrationTxService;
    private final EmailSender emailSender;

    public RegistrationServiceImpl(UserRepository userRepository,
            PendingRegistrationRepository pendingRegistrationRepository,
            RegistrationTxService registrationTxService, EmailSender emailSender) {
        this.userRepository = userRepository;
        this.pendingRegistrationRepository = pendingRegistrationRepository;
        this.registrationTxService = registrationTxService;
        this.emailSender = emailSender;
    }

    @Override
    public void requestRegistration(String username, String name, String email, String rawPassword) {
        String normalizedEmail = EmailAddresses.normalize(email);
        // Fail before BCrypt/hashing work: the same checks run again inside the transaction.
        if (userRepository.existsByUsername(username)) {
            throw new DuplicateUsernameException();
        }
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateEmailException();
        }
        pendingRegistrationRepository.findByUsername(username)
                .filter(draft -> !draft.getEmail().equals(normalizedEmail))
                .ifPresent(draft -> {
                    throw new DuplicateUsernameException();
                });
        VerificationTicket ticket;
        try {
            ticket = registrationTxService.createPendingRegistration(username, name.trim(), normalizedEmail,
                    rawPassword);
        } catch (DataIntegrityViolationException e) {
            // Two concurrent requests can pass the checks above; the UNIQUE keys are the last line.
            if (mentionsConstraint(e, PENDING_USERNAME_UNIQUE_CONSTRAINT)) {
                throw new DuplicateUsernameException();
            }
            if (mentionsConstraint(e, PENDING_EMAIL_UNIQUE_CONSTRAINT)) {
                throw new DuplicateEmailException();
            }
            throw e;
        }
        emailSender.sendVerificationCode(ticket.email(), ticket.code(), EmailPurpose.REGISTRATION);
    }

    @Override
    public void confirmRegistration(String email, String code) {
        try {
            RegistrationConfirmationResult result =
                    registrationTxService.confirmRegistration(EmailAddresses.normalize(email), code);
            if (result instanceof RegistrationConfirmationResult.InvalidCode) {
                throw new InvalidVerificationCodeException();
            }
        } catch (DataIntegrityViolationException e) {
            // The transaction has already rolled back; map only the two register constraints.
            if (mentionsConstraint(e, USERNAME_UNIQUE_CONSTRAINT)) {
                throw new DuplicateUsernameException();
            }
            if (mentionsConstraint(e, EMAIL_UNIQUE_CONSTRAINT)) {
                throw new DuplicateEmailException();
            }
            throw e;
        }
    }

    private static boolean mentionsConstraint(DataIntegrityViolationException e, String constraint) {
        for (Throwable t = e.getCause(); t != null; t = t.getCause()) {
            if (t instanceof SQLIntegrityConstraintViolationException sql
                    && sql.getMessage() != null
                    && sql.getMessage().contains(constraint)) {
                return true;
            }
        }
        return false;
    }
}