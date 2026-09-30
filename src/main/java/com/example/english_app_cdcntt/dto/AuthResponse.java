package com.example.english_app_cdcntt.dto;

/**
 * Success body of login and refresh (§5.1:230) — exactly these three fields. {@code expiresIn}
 * is the access token's lifetime in seconds, not a timestamp (§4:179).
 */
public record AuthResponse(String accessToken, String refreshToken, long expiresIn) {
}
