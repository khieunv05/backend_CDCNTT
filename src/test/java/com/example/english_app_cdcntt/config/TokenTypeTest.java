package com.example.english_app_cdcntt.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plain unit tests for {@link TokenType}. No Spring context, no database.
 */
class TokenTypeTest {

    @Test
    @DisplayName("ACCESS claim value is \"access\"")
    void access_claimValue_shouldReturnAccess() {
        assertThat(TokenType.ACCESS.claimValue()).isEqualTo("access");
    }

    @Test
    @DisplayName("REFRESH claim value is \"refresh\"")
    void refresh_claimValue_shouldReturnRefresh() {
        assertThat(TokenType.REFRESH.claimValue()).isEqualTo("refresh");
    }

    @Test
    @DisplayName("fromClaim(\"access\") returns ACCESS")
    void fromClaim_withAccessValue_shouldReturnAccess() {
        assertThat(TokenType.fromClaim("access")).isEqualTo(Optional.of(TokenType.ACCESS));
    }

    @Test
    @DisplayName("fromClaim(\"refresh\") returns REFRESH")
    void fromClaim_withRefreshValue_shouldReturnRefresh() {
        assertThat(TokenType.fromClaim("refresh")).isEqualTo(Optional.of(TokenType.REFRESH));
    }

    @Test
    @DisplayName("fromClaim(unknown) returns empty")
    void fromClaim_withUnknownValue_shouldReturnEmpty() {
        assertThat(TokenType.fromClaim("nope")).isEmpty();
    }

    @Test
    @DisplayName("fromClaim(null) returns empty")
    void fromClaim_withNull_shouldReturnEmpty() {
        assertThat(TokenType.fromClaim(null)).isEmpty();
    }

    @Test
    @DisplayName("fromClaim is case-sensitive")
    void fromClaim_withUppercaseValue_shouldReturnEmpty() {
        assertThat(TokenType.fromClaim("ACCESS")).isEmpty();
        assertThat(TokenType.fromClaim("REFRESH")).isEmpty();
    }
}
