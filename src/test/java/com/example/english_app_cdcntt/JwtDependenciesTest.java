package com.example.english_app_cdcntt;

import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies that jjwt's runtime implementation and JSON adapter work together. */
class JwtDependenciesTest {

    @Test
    void jwtRuntime_withNestedClaims_shouldSignAndParse() {
        var key = Jwts.SIG.HS256.key().build();
        var token = Jwts.builder()
                .subject("dependency-smoke-test")
                .claim("metadata", Map.of("type", "access"))
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        var claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertThat(claims.getSubject()).isEqualTo("dependency-smoke-test");
        assertThat(claims.get("metadata")).isEqualTo(Map.of("type", "access"));
    }
}
