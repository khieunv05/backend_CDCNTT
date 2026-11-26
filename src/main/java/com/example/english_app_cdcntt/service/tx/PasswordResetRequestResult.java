package com.example.english_app_cdcntt.service.tx;

/**
 * Result of asking for a new reset code. {@link Suppressed} covers the resend cooldown and the
 * hourly quota: the endpoint still answers 200 and sends nothing, because saying "too many
 * requests" would reveal that the email is registered.
 */
public sealed interface PasswordResetRequestResult {

    record Issued(VerificationTicket ticket) implements PasswordResetRequestResult {
    }

    record Suppressed() implements PasswordResetRequestResult {
    }
}
