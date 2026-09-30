package com.example.english_app_cdcntt.service.tx;

import com.example.english_app_cdcntt.dto.AuthResponse;

/**
 * Outcome of one §5.2 rotation attempt, decided inside {@code RefreshTokenTxService}'s
 * transaction and consumed by {@code AuthService} only after that transaction ended — so the
 * EXPIRED-row deletion is already committed when the 401 is thrown (§5.2:240).
 */
public sealed interface RefreshRotationResult {

    /** No matching row, or the row is live but the presented JWT fails signature/type/owner/expiry consistency checks (§5.2:239,241). */
    record Invalid() implements RefreshRotationResult {
    }

    /** Row existed but {@code expiryDate <= now}; the row was deleted and the deletion committed (§5.2:240). */
    record Expired() implements RefreshRotationResult {
    }

    /** Old row deleted, fresh pair with new jti stored in the same transaction (§5.2:242). */
    record Rotated(AuthResponse response) implements RefreshRotationResult {
    }
}
