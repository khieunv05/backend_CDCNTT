package com.example.english_app_cdcntt.service.tx;

import com.example.english_app_cdcntt.config.OtpProperties;
import com.example.english_app_cdcntt.entity.PendingRegistration;
import com.example.english_app_cdcntt.entity.User;
import com.example.english_app_cdcntt.exception.DuplicateEmailException;
import com.example.english_app_cdcntt.exception.DuplicateUsernameException;
import com.example.english_app_cdcntt.repository.PendingRegistrationRepository;
import com.example.english_app_cdcntt.repository.UserRepository;
import com.example.english_app_cdcntt.service.OneTimeCodes;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The two short write transactions of registration. Nothing here sends mail: the raw code is
 * returned to the coordinator so SMTP only runs after the commit (§1.4 — a mail round-trip must
 * never sit inside a transaction), and the code is stored only as its SHA-256 hash.
 *
 * <p>The username/email UNIQUE constraints stay the last line of defense: a lost race surfaces as
 * {@code DataIntegrityViolationException} after this transaction rolled back, and the coordinator
 * maps only the two {@code users} constraints to 409.
 */
@Service
public class RegistrationTxService {

    private final UserRepository userRepository;
    private final PendingRegistrationRepository pendingRegistrationRepository;
    private final PasswordEncoder passwordEncoder;
    private final OneTimeCodes oneTimeCodes;
    private final OtpProperties otpProperties;
    private final Clock clock;

    public RegistrationTxService(UserRepository userRepository,
            PendingRegistrationRepository pendingRegistrationRepository, PasswordEncoder passwordEncoder,
            OneTimeCodes oneTimeCodes, OtpProperties otpProperties, Clock clock) {
        this.userRepository = userRepository;
        this.pendingRegistrationRepository = pendingRegistrationRepository;
        this.passwordEncoder = passwordEncoder;
        this.oneTimeCodes = oneTimeCodes;
        this.otpProperties = otpProperties;
        this.clock = clock;
    }

    /**
     * Creates or replaces the draft of an unverified registration. Re-registering with the same
     * email overwrites the draft (the mailbox owner decides), while a username held by another
     * mailbox answers 409 immediately instead of after the user types a code.
     */
    @Transactional
    public VerificationTicket createPendingRegistration(String username, String name, String email, String rawPassword) {
        Instant now = clock.instant();
        if (userRepository.existsByUsername(username)) {
            throw new DuplicateUsernameException();
        }
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateEmailException();
        }
        rejectUsernameHeldByAnotherDraft(username, email);

        // An expired draft is refreshed in place, never deleted. With IDENTITY ids Hibernate runs the
        // INSERT at persist() time and queues the DELETE for flush, so delete-then-create would hit
        // UNIQUE(email) as a DataIntegrityViolationException (500). Re-requesting simply replaces the
        // code; the demo has no resend cooldown or hourly send quota.
        PendingRegistration draft = pendingRegistrationRepository.findByEmail(email).orElse(null);

        String code = oneTimeCodes.issue();
        String codeHash = oneTimeCodes.hash(code);
        Instant expiresAt = now.plus(otpProperties.registrationTtl());
        PendingRegistration saved;
        if (draft == null) {
            saved = PendingRegistration.create(username, name, email, passwordEncoder.encode(rawPassword),
                    codeHash, expiresAt, now);
        } else {
            draft.refresh(username, name, passwordEncoder.encode(rawPassword), codeHash, expiresAt);
            saved = draft;
        }
        pendingRegistrationRepository.save(saved);
        return new VerificationTicket(email, code);
    }

    /**
     * Accepts the code and creates the account in one transaction. Every failure answers the same
     * {@link RegistrationConfirmationResult.InvalidCode}; the fifth wrong guess deletes the draft.
     */
    @Transactional
    public RegistrationConfirmationResult confirmRegistration(String email, String code) {
        Instant now = clock.instant();
        Optional<PendingRegistration> found = pendingRegistrationRepository.findByEmail(email);
        if (found.isEmpty()) {
            return new RegistrationConfirmationResult.InvalidCode();
        }
        PendingRegistration draft = found.get();
        if (draft.isExpired(now)) {
            pendingRegistrationRepository.delete(draft);
            return new RegistrationConfirmationResult.InvalidCode();
        }
        if (!oneTimeCodes.matches(code, draft.getCodeHash())) {
            draft.incrementAttempts();
            if (draft.getAttempts() >= otpProperties.maxAttempts()) {
                pendingRegistrationRepository.delete(draft);
            }
            return new RegistrationConfirmationResult.InvalidCode();
        }
        pendingRegistrationRepository.delete(draft);
        userRepository.save(User.create(draft.getUsername(), draft.getName(), draft.getEmail(), draft.getPassword()));
        return new RegistrationConfirmationResult.Confirmed();
    }

    private void rejectUsernameHeldByAnotherDraft(String username, String email) {
        pendingRegistrationRepository.findByUsername(username)
                .filter(draft -> !draft.getEmail().equals(email))
                .ifPresent(draft -> {
                    throw new DuplicateUsernameException();
                });
    }
}
