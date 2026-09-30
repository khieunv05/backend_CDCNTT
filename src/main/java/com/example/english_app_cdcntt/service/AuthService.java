package com.example.english_app_cdcntt.service;

import com.example.english_app_cdcntt.dto.AuthResponse;
import com.example.english_app_cdcntt.exception.DuplicateUsernameException;
import com.example.english_app_cdcntt.exception.InvalidCredentialsException;
import com.example.english_app_cdcntt.exception.TokenExpiredException;

/**
 * Contract of the four auth endpoints (§5.2). {@code AuthController} and the tests depend only on
 * this interface; the bean is {@code service.impl.AuthServiceImpl} (§3 places service contracts in
 * {@code service/} and their implementations in {@code service/impl/}).
 *
 * <p>No method here opens a transaction around the HTTP call: every write happens in a named tx
 * bean, so a 401 can only be thrown after that transaction already ended (§5.2:234,238–243).
 */
public interface AuthService {

    /**
     * §5.2:234 — create the account; the UNIQUE constraint stays the last line of defense.
     *
     * @throws DuplicateUsernameException when the username is already taken
     */
    void register(String username, String rawPassword);

    /**
     * §5.2:235 — verify the password, then open a session; returns only after the refresh row is committed.
     *
     * @throws InvalidCredentialsException when the account does not exist or the password does not match
     */
    AuthResponse login(String username, String rawPassword);

    /**
     * §5.2:238–243 — rotate the presented refresh token; both 401 flavors are thrown post-commit.
     *
     * @throws TokenExpiredException       when the stored row was expired (and has now been deleted)
     * @throws InvalidCredentialsException when the row is unknown or the presented JWT fails consistency checks
     */
    AuthResponse refresh(String presentedToken);

    /** §5.2:237 — revoke the session; a token no longer stored still answers 200 (idempotent). */
    void logout(String presentedToken);
}
