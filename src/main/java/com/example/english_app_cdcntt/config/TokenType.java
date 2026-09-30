package com.example.english_app_cdcntt.config;

import java.util.Optional;

/** Value of the JWT {@code type} claim; a refresh token must never be accepted as an access token. */
public enum TokenType {
    ACCESS("access"),
    REFRESH("refresh");

    private final String claimValue;

    TokenType(String claimValue) {
        this.claimValue = claimValue;
    }

    public String claimValue() {
        return claimValue;
    }

    public static Optional<TokenType> fromClaim(String claimValue) {
        for (TokenType type : values()) {
            if (type.claimValue.equals(claimValue)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
