package com.example.english_app_cdcntt.service.impl;

import com.example.english_app_cdcntt.dto.AuthResponse;
import com.example.english_app_cdcntt.entity.User;
import com.example.english_app_cdcntt.exception.DuplicateUsernameException;
import com.example.english_app_cdcntt.exception.InvalidCredentialsException;
import com.example.english_app_cdcntt.exception.PasswordReuseException;
import com.example.english_app_cdcntt.exception.TokenExpiredException;
import com.example.english_app_cdcntt.exception.WrongPasswordException;
import com.example.english_app_cdcntt.repository.UserRepository;
import com.example.english_app_cdcntt.service.AuthService;
import com.example.english_app_cdcntt.service.tx.PasswordTxService;
import com.example.english_app_cdcntt.service.tx.RefreshRotationResult;
import com.example.english_app_cdcntt.service.tx.RefreshTokenTxService;
import com.example.english_app_cdcntt.service.tx.RegistrationTxService;
import java.sql.SQLIntegrityConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Coordinator for the four auth endpoints (§5.2). Deliberately NOT transactional itself: every
 * write happens in a named tx bean, and this class may only throw after that transaction has
 * ended — the EXPIRED refresh deletion must be committed before its 401 surfaces (:240), and a
 * lost register race must be mapped outside the rolled-back transaction (:234).
 */
@Service
public class AuthServiceImpl implements AuthService {

    /** Constraint name from V1__init_schema.sql:8 — the ONLY integrity violation mapped to 409. */
    private static final String USERNAME_UNIQUE_CONSTRAINT = "uk_users_username";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RegistrationTxService registrationTxService;
    private final RefreshTokenTxService refreshTokenTxService;
    private final PasswordTxService passwordTxService;

    public AuthServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder,
            RegistrationTxService registrationTxService, RefreshTokenTxService refreshTokenTxService,
            PasswordTxService passwordTxService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.registrationTxService = registrationTxService;
        this.refreshTokenTxService = refreshTokenTxService;
        this.passwordTxService = passwordTxService;
    }

    /** §5.2:234 — pre-check inside the short transaction, UNIQUE constraint as last line of defense. */
    @Override
    public void register(String username, String name, String rawPassword) {
        try {
            registrationTxService.createUser(username, name, rawPassword);
        } catch (DataIntegrityViolationException e) {
            // The transaction has already rolled back at the proxy boundary; map only the
            // username-unique violation, rethrow anything else (§5.2:234).
            if (isUsernameUniqueViolation(e)) {
                throw new DuplicateUsernameException();
            }
            throw e;
        }
    }

    /** §5.2:235 — verify password, then issue + store; success returns only after commit. */
    @Override
    public AuthResponse login(String username, String rawPassword) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new InvalidCredentialsException();
        }
        return refreshTokenTxService.issueSession(user);
    }

    /** §5.2:238–243 — rotate through the tx bean; both 401 flavors are thrown post-commit. */
    @Override
    public AuthResponse refresh(String presentedToken) {
        RefreshRotationResult result = refreshTokenTxService.attemptRotation(presentedToken);
        return switch (result) {
            case RefreshRotationResult.Rotated rotated -> rotated.response();
            case RefreshRotationResult.Expired expired -> throw new TokenExpiredException();
            case RefreshRotationResult.Invalid invalid -> throw new InvalidCredentialsException();
        };
    }

    /** §5.2:237 — exact-string delete; a token no longer in the DB still answers 200. */
    @Override
    public void logout(String presentedToken) {
        refreshTokenTxService.revoke(presentedToken);
    }

    /**
     * Verifies the current password before any write; both 400 flavors are thrown here, outside
     * the transaction, so a rejected attempt never touches the stored hash or the sessions.
     */
    @Override
    public void changePassword(String username, String currentPassword, String newPassword) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new WrongPasswordException();
        }
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new PasswordReuseException();
        }
        passwordTxService.changePasswordAndRevokeSessions(user.getId(), passwordEncoder.encode(newPassword));
    }

    private static boolean isUsernameUniqueViolation(DataIntegrityViolationException e) {
        for (Throwable t = e.getCause(); t != null; t = t.getCause()) {
            if (t instanceof SQLIntegrityConstraintViolationException sql
                    && sql.getMessage() != null
                    && sql.getMessage().contains(USERNAME_UNIQUE_CONSTRAINT)) {
                return true;
            }
        }
        return false;
    }
}