package com.example.english_app_cdcntt.service;

/** The two steps of password recovery; email is the only accepted identifier (no username). */
public interface PasswordResetService {

    /**
     * Always answers success: an unknown email and a mail failure look the same from the outside,
     * so the endpoint cannot be used to probe registrations.
     */
    void requestReset(String email);

    /** Applies the new password and revokes every session of the account; never issues a new one. */
    void resetPassword(String email, String code, String newPassword);
}
