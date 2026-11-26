package com.example.english_app_cdcntt.service;

import com.example.english_app_cdcntt.dto.AuthResponse;

/**
 * Session and credential endpoints (§5.2). Registration lives in {@link RegistrationService} and
 * recovery in {@link PasswordResetService}: both have a two-step, mail-bearing flow that does not
 * belong in the session coordinator.
 */
public interface AuthService {

    /** Verifies username + password, then issues and stores a refresh token. */
    AuthResponse login(String username, String rawPassword);

    /** Rotates a refresh token: the old row is deleted, a fresh pair is returned. */
    AuthResponse refresh(String presentedToken);

    /** Revokes the presented refresh token; an unknown token still answers success. */
    void logout(String presentedToken);

    /** Verifies the current password, rejects reuse, then revokes every other session. */
    void changePassword(String username, String currentPassword, String newPassword);
}
