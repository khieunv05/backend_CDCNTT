package com.example.english_app_cdcntt.service.tx;

import com.example.english_app_cdcntt.config.OtpProperties;
import com.example.english_app_cdcntt.entity.PasswordResetCode;
import com.example.english_app_cdcntt.entity.User;
import com.example.english_app_cdcntt.exception.InvalidCredentialsException;
import com.example.english_app_cdcntt.repository.PasswordResetCodeRepository;
import com.example.english_app_cdcntt.repository.RefreshTokenRepository;
import com.example.english_app_cdcntt.repository.UserRepository;
import com.example.english_app_cdcntt.service.OneTimeCodes;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The two write transactions behind password recovery. Like registration, this class never talks
 * SMTP: it returns the raw code and the coordinator mails it after the commit.
 *
 * <p>A successful reset replaces the BCrypt hash and deletes every refresh token of the account in
 * one commit — that deletion is what makes the change "log every device out".
 */
@Service
public class PasswordResetTxService {

    private final UserRepository userRepository;
    private final PasswordResetCodeRepository passwordResetCodeRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OneTimeCodes oneTimeCodes;
    private final OtpProperties otpProperties;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public PasswordResetTxService(UserRepository userRepository,
            PasswordResetCodeRepository passwordResetCodeRepository, RefreshTokenRepository refreshTokenRepository,
            OneTimeCodes oneTimeCodes, OtpProperties otpProperties, PasswordEncoder passwordEncoder, Clock clock) {
        this.userRepository = userRepository;
        this.passwordResetCodeRepository = passwordResetCodeRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.oneTimeCodes = oneTimeCodes;
        this.otpProperties = otpProperties;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    /**
     * Issues (or replaces) the single live code of an account. Re-requesting immediately simply
     * replaces the code, keeping the endpoint silent either way.
     */
    @Transactional
    public VerificationTicket requestReset(Long userId) {
        Instant now = clock.instant();
        User user = userRepository.findById(userId).orElseThrow(InvalidCredentialsException::new);

        // Same rule as registration: refresh the expired row instead of delete-then-create, because
        // the INSERT of a delete/insert pair reaches MySQL before the DELETE is flushed.
        PasswordResetCode current = passwordResetCodeRepository.findByUser_Id(userId).orElse(null);

        String code = oneTimeCodes.issue();
        String codeHash = oneTimeCodes.hash(code);
        Instant expiresAt = now.plus(otpProperties.resetTtl());
        PasswordResetCode saved;
        if (current == null) {
            saved = PasswordResetCode.create(user, codeHash, expiresAt, now);
        } else {
            current.refresh(codeHash, expiresAt);
            saved = current;
        }
        passwordResetCodeRepository.save(saved);
        return new VerificationTicket(user.getEmail(), code);
    }

    /**
     * Validates the code first, then rejects a password equal to the current one, replaces the hash
     * and revokes every session — all in one commit. The order matters: checking reuse first would
     * let a caller learn that an email belongs to an account without owning the code.
     */
    @Transactional
    public PasswordResetResult resetPassword(Long userId, String code, String rawPassword) {
        Instant now = clock.instant();
        Optional<PasswordResetCode> found = passwordResetCodeRepository.findByUser_Id(userId);
        if (found.isEmpty()) {
            return new PasswordResetResult.InvalidCode();
        }
        PasswordResetCode resetCode = found.get();
        if (resetCode.isExpired(now)) {
            passwordResetCodeRepository.delete(resetCode);
            return new PasswordResetResult.InvalidCode();
        }
        if (!oneTimeCodes.matches(code, resetCode.getCodeHash())) {
            resetCode.incrementAttempts();
            if (resetCode.getAttempts() >= otpProperties.maxAttempts()) {
                passwordResetCodeRepository.delete(resetCode);
            }
            return new PasswordResetResult.InvalidCode();
        }
        User user = userRepository.findById(userId).orElseThrow(InvalidCredentialsException::new);
        if (passwordEncoder.matches(rawPassword, user.getPassword())) {
            return new PasswordResetResult.PasswordReused();
        }
        user.changePassword(passwordEncoder.encode(rawPassword));
        refreshTokenRepository.deleteByUser_Id(userId);
        passwordResetCodeRepository.delete(resetCode);
        return new PasswordResetResult.Completed();
    }
}
