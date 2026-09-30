package com.example.english_app_cdcntt.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Issues and parses HS256 JWTs per plan §5.2: claims {@code sub}=username, {@code uid}=user id,
 * {@code type}=access|refresh, {@code iat}, {@code exp}, plus a random {@code jti} on every token
 * so two tokens issued in the same second are never equal. {@link JwtProperties} validates secret
 * strength at startup; this class only decodes it. Timestamps are truncated to whole seconds
 * because JWT NumericDate has second precision — the refresh-token row stores the exact same
 * {@code exp} instant, so rotation can require JWT/DB consistency.
 */
@Service
public class JwtService {

    static final String CLAIM_TYPE = "type";
    static final String CLAIM_UID = "uid";

    private final SecretKey key;
    private final JwtProperties properties;
    private final Clock clock;

    public JwtService(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(properties.secret().value()));
    }

    /** A freshly signed token plus the exact instants encoded in it. */
    public record IssuedToken(String token, Instant issuedAt, Instant expiresAt) {
    }

    /** Verified claims of a parsed token. */
    public record JwtPayload(String username, Long userId, TokenType type, String jti,
            Instant issuedAt, Instant expiresAt) {
    }

    public IssuedToken issue(TokenType type, Long userId, String username) {
        Instant issuedAt = Instant.now(clock).truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plus(expirationFor(type));
        String token = Jwts.builder()
                .subject(username)
                .claim(CLAIM_UID, userId)
                .claim(CLAIM_TYPE, type.claimValue())
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
        return new IssuedToken(token, issuedAt, expiresAt);
    }

    /**
     * Verifies signature and expiry against the injected clock.
     *
     * @throws JwtException if malformed, tampered, expired, or carrying unknown/incomplete claims
     */
    public JwtPayload parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .clock(() -> Date.from(Instant.now(clock)))
                .build()
                .parseSignedClaims(token)
                .getPayload();
        TokenType type = TokenType.fromClaim(claims.get(CLAIM_TYPE, String.class))
                .orElseThrow(() -> new UnsupportedJwtException("Unknown JWT type claim"));
        Number userId = claims.get(CLAIM_UID, Number.class);
        if (userId == null) {
            throw new MalformedJwtException("Missing uid claim");
        }
        if (claims.getSubject() == null || claims.getId() == null
                || claims.getIssuedAt() == null || claims.getExpiration() == null) {
            throw new MalformedJwtException("Missing required JWT claim");
        }
        return new JwtPayload(claims.getSubject(), userId.longValue(), type, claims.getId(),
                claims.getIssuedAt().toInstant(), claims.getExpiration().toInstant());
    }

    public long accessTokenExpiresInSeconds() {
        return properties.accessTokenExpiration().toSeconds();
    }

    private Duration expirationFor(TokenType type) {
        return type == TokenType.ACCESS
                ? properties.accessTokenExpiration()
                : properties.refreshTokenExpiration();
    }
}
