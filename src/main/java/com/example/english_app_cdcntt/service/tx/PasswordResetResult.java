package com.example.english_app_cdcntt.service.tx;

/** Outcome of one reset attempt; the attempt counter and the deleted code are committed first. */
public sealed interface PasswordResetResult {

    /** Password replaced and every refresh token of the account revoked in the same commit. */
    record Completed() implements PasswordResetResult {
    }

    record InvalidCode() implements PasswordResetResult {
    }

    /**
     * The code was valid but the typed password is the current one. Answered only after the code
     * check, so an unknown mailbox and a live account look alike from the outside.
     */
    record PasswordReused() implements PasswordResetResult {
    }
}
