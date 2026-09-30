package com.example.english_app_cdcntt.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Base64;

@ConfigurationProperties("app.jwt")
@Validated
public record JwtProperties(
        @NotNull SecretValue secret,
        @NotNull @DefaultValue("30m") Duration accessTokenExpiration,
        @NotNull @DefaultValue("7d") Duration refreshTokenExpiration) {

    @AssertTrue(message = "JWT secret must be externally supplied Base64 encoding at least 32 bytes")
    public boolean isSecretValid() {
        if (secret == null || !secret.isConfigured()) {
            return false;
        }
        try {
            return Base64.getDecoder().decode(secret.value()).length >= 32;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    @AssertTrue(message = "Access token expiration must be positive")
    public boolean isAccessTokenExpirationValid() {
        return accessTokenExpiration != null && accessTokenExpiration.isPositive();
    }

    @AssertTrue(message = "Refresh token expiration must be positive")
    public boolean isRefreshTokenExpirationValid() {
        return refreshTokenExpiration != null && refreshTokenExpiration.isPositive();
    }
}
