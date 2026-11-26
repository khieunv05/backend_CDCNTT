package com.example.english_app_cdcntt.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Issues and verifies the six-digit codes mailed to users. Only the SHA-256 hash is stored, so a
 * database dump never contains the plaintext code. Hashing alone cannot make a six-digit space
 * unguessable; the TTL and the five-attempt limit are what make brute force impractical, and they
 * must stay in place if this class changes.
 */
@Component
public class OneTimeCodes {

    private static final int CODE_BOUND = 1_000_000;
    private static final String DIGEST_ALGORITHM = "SHA-256";

    private final SecureRandom random;

    public OneTimeCodes() {
        this(new SecureRandom());
    }

    /** Test seam: a seeded {@link SecureRandom} makes {@link #issue()} deterministic. */
    OneTimeCodes(SecureRandom random) {
        this.random = random;
    }

    /** Six digits, leading zeroes preserved ({@code 000123} is a valid code). */
    public String issue() {
        return String.format(Locale.ROOT, "%06d", random.nextInt(CODE_BOUND));
    }

    public String hash(String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance(DIGEST_ALGORITHM);
            return HexFormat.of().formatHex(digest.digest(code.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 must be available on every JVM", e);
        }
    }

    /** Constant-time comparison of the presented code against the stored hash. */
    public boolean matches(String presentedCode, String storedHash) {
        return MessageDigest.isEqual(hash(presentedCode).getBytes(StandardCharsets.US_ASCII),
                storedHash.getBytes(StandardCharsets.US_ASCII));
    }
}
