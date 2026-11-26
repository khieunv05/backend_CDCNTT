package com.example.english_app_cdcntt.service.tx;

/**
 * Outcome of one confirmation attempt, decided inside {@code RegistrationTxService}'s transaction
 * and consumed by the service only after that transaction ended — so the failed-attempt counter is
 * already committed when the 400 is thrown.
 */
public sealed interface RegistrationConfirmationResult {

    record Confirmed() implements RegistrationConfirmationResult {
    }

    /** No draft, expired code, or wrong code; the attempt counter was already updated. */
    record InvalidCode() implements RegistrationConfirmationResult {
    }
}
