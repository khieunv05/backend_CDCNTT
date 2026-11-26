package com.example.english_app_cdcntt.service;

/** The two steps of account creation: request a code, then confirm it. */
public interface RegistrationService {

    /**
     * §5.2:234 — writes the pending draft and mails the code. Duplicate username/email answer 409,
     * a resend inside the cooldown answers 429, and a mail failure answers 502 while the draft stays.
     */
    void requestRegistration(String username, String name, String email, String rawPassword);

    /** §5.2 — creates the account once the code matches; every failure answers the same 400. */
    void confirmRegistration(String email, String code);
}
