package com.example.english_app_cdcntt.config;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Plain unit tests for {@link JwtService}. No Spring context, no database.
 */
class JwtServiceTest {

    /** Base64 of 32 ASCII bytes -> decodes to 32 bytes, enough for HS256. */
    private static final String SECRET = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

    /** A different, equally valid secret used to prove signature verification. */
    private static final String OTHER_SECRET = Base64.getEncoder()
            .encodeToString("fedcba9876543210fedcba9876543210".getBytes(StandardCharsets.UTF_8));

    private static final Duration ACCESS_EXPIRATION = Duration.ofMinutes(30);
    private static final Duration REFRESH_EXPIRATION = Duration.ofDays(7);

    private static final Instant BASE_INSTANT = Instant.parse("2026-01-01T12:00:00Z");
    private static final Clock BASE_CLOCK = Clock.fixed(BASE_INSTANT, ZoneOffset.UTC);

    private static JwtService serviceWith(String secret, Duration access, Duration refresh, Clock clock) {
        JwtProperties properties = new JwtProperties(new SecretValue(secret), access, refresh);
        return new JwtService(properties, clock);
    }

    private static JwtService serviceWith(Duration access, Duration refresh, Clock clock) {
        return serviceWith(SECRET, access, refresh, clock);
    }

    private static JwtService defaultService() {
        return serviceWith(ACCESS_EXPIRATION, REFRESH_EXPIRATION, BASE_CLOCK);
    }

    @Test
    @DisplayName("issue(ACCESS) round-trips through parse")
    void issue_withAccessType_shouldRoundTripThroughParse() {
        // Given
        JwtService service = defaultService();

        // When
        JwtService.IssuedToken issued = service.issue(TokenType.ACCESS, 42L, "alice");
        JwtService.JwtPayload payload = service.parse(issued.token());

        // Then
        assertThat(payload.username()).isEqualTo("alice");
        assertThat(payload.userId()).isEqualTo(42L);
        assertThat(payload.type()).isEqualTo(TokenType.ACCESS);
        assertThat(payload.jti()).isNotNull().isNotBlank();
        assertThat(payload.issuedAt()).isEqualTo(issued.issuedAt());
        assertThat(payload.expiresAt()).isEqualTo(issued.expiresAt());
    }

    @Test
    @DisplayName("issue(REFRESH) round-trips through parse")
    void issue_withRefreshType_shouldRoundTripThroughParse() {
        // Given
        JwtService service = defaultService();

        // When
        JwtService.IssuedToken issued = service.issue(TokenType.REFRESH, 7L, "bob");
        JwtService.JwtPayload payload = service.parse(issued.token());

        // Then
        assertThat(payload.username()).isEqualTo("bob");
        assertThat(payload.userId()).isEqualTo(7L);
        assertThat(payload.type()).isEqualTo(TokenType.REFRESH);
        assertThat(payload.jti()).isNotNull().isNotBlank();
        assertThat(payload.issuedAt()).isEqualTo(issued.issuedAt());
        assertThat(payload.expiresAt()).isEqualTo(issued.expiresAt());
    }

    @Test
    @DisplayName("ACCESS tokens expire after the configured access duration")
    void issue_withAccessType_shouldUseAccessExpiration() {
        // Given
        JwtService service = defaultService();

        // When
        JwtService.IssuedToken issued = service.issue(TokenType.ACCESS, 1L, "alice");

        // Then
        assertThat(issued.expiresAt()).isEqualTo(issued.issuedAt().plus(Duration.ofMinutes(30)));
    }

    @Test
    @DisplayName("REFRESH tokens expire after the configured refresh duration")
    void issue_withRefreshType_shouldUseRefreshExpiration() {
        // Given
        JwtService service = defaultService();

        // When
        JwtService.IssuedToken issued = service.issue(TokenType.REFRESH, 1L, "alice");

        // Then
        assertThat(issued.expiresAt()).isEqualTo(issued.issuedAt().plus(Duration.ofDays(7)));
    }

    @Test
    @DisplayName("issuedAt is truncated to whole seconds")
    void issue_shouldTruncateIssuedAtToWholeSeconds() {
        // Given
        Clock subSecondClock = Clock.fixed(Instant.parse("2026-01-01T12:00:00.987654321Z"), ZoneOffset.UTC);
        JwtService service = serviceWith(ACCESS_EXPIRATION, REFRESH_EXPIRATION, subSecondClock);

        // When
        JwtService.IssuedToken issued = service.issue(TokenType.ACCESS, 5L, "carol");
        JwtService.JwtPayload payload = service.parse(issued.token());

        // Then
        assertThat(issued.issuedAt()).isEqualTo(Instant.parse("2026-01-01T12:00:00Z"));
        assertThat(issued.issuedAt().getNano()).isZero();
        assertThat(payload.issuedAt()).isEqualTo(issued.issuedAt());
        assertThat(payload.issuedAt().getNano()).isZero();
    }

    @Test
    @DisplayName("accessTokenExpiresInSeconds returns the access duration in seconds")
    void accessTokenExpiresInSeconds_shouldReturnConfiguredSeconds() {
        // Given
        JwtService service = defaultService();

        // When
        long seconds = service.accessTokenExpiresInSeconds();

        // Then
        assertThat(seconds).isEqualTo(1800L);
    }

    @Test
    @DisplayName("parse rejects a token with a tampered signature")
    void parse_withTamperedSignature_shouldThrowJwtException() {
        // Given
        JwtService service = defaultService();
        String token = service.issue(TokenType.ACCESS, 42L, "alice").token();
        String tampered = tamperSignature(token);

        // When / Then
        assertThat(tampered).isNotEqualTo(token);
        assertThatThrownBy(() -> service.parse(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("parse rejects a token signed with a different key")
    void parse_withTokenSignedByDifferentKey_shouldThrowJwtException() {
        // Given
        JwtService service = defaultService();
        JwtService otherService = serviceWith(OTHER_SECRET, ACCESS_EXPIRATION, REFRESH_EXPIRATION, BASE_CLOCK);
        String foreignToken = otherService.issue(TokenType.ACCESS, 42L, "alice").token();

        // When / Then
        assertThatThrownBy(() -> service.parse(foreignToken)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("parse rejects a malformed token")
    void parse_withMalformedToken_shouldThrowJwtException() {
        // Given
        JwtService service = defaultService();

        // When / Then
        assertThatThrownBy(() -> service.parse("not-a-jwt")).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("parse rejects an expired token")
    void parse_withExpiredToken_shouldThrowExpiredJwtException() {
        // Given
        Duration oneMinute = Duration.ofMinutes(1);
        JwtProperties properties = new JwtProperties(new SecretValue(SECRET), oneMinute, REFRESH_EXPIRATION);
        JwtService issuer = new JwtService(properties, BASE_CLOCK);
        JwtService.IssuedToken issued = issuer.issue(TokenType.ACCESS, 9L, "dave");

        Clock laterClock = Clock.fixed(BASE_INSTANT.plus(Duration.ofMinutes(2)), ZoneOffset.UTC);
        JwtService verifier = new JwtService(properties, laterClock);

        // When / Then
        assertThatThrownBy(() -> verifier.parse(issued.token()))
                .isInstanceOf(ExpiredJwtException.class)
                .isInstanceOf(JwtException.class);
    }

    /**
     * Replaces the first character of the signature segment with a different base64url character.
     * The first signature character carries significant bits, so the decoded signature always changes.
     */
    private static String tamperSignature(String token) {
        int lastDot = token.lastIndexOf('.');
        String head = token.substring(0, lastDot + 1);
        String signature = token.substring(lastDot + 1);
        char first = signature.charAt(0);
        char replacement = first == 'A' ? 'B' : 'A';
        return head + replacement + signature.substring(1);
    }
}
