package com.example.english_app_cdcntt.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * §1.1 requires the stored password hash to use BCrypt cost 12. Every other auth test mocks
 * {@code PasswordEncoder}, so this is the only place the real encoder parameters are asserted.
 * No Spring context: {@link SecurityConfig} is instantiated directly.
 */
class SecurityConfigTest {

    private final SecurityConfig securityConfig = new SecurityConfig();

    @Test
    @DisplayName("passwordEncoder hashes with BCrypt cost 12 (§1.1)")
    void passwordEncoderUsesCostTwelve() {
        String hash = securityConfig.passwordEncoder().encode("s3cret!x");

        // BCryptPasswordEncoder's default would be $2a$10$ — the plan explicitly requires 12.
        assertThat(hash).startsWith("$2a$12$");
    }

    @Test
    @DisplayName("passwordEncoder verifies its own hash and rejects a wrong password")
    void passwordEncoderRoundTrip() {
        PasswordEncoder encoder = securityConfig.passwordEncoder();
        String hash = encoder.encode("s3cret!x");

        assertThat(encoder.matches("s3cret!x", hash)).isTrue();
        assertThat(encoder.matches("s3cret!y", hash)).isFalse();
    }
}