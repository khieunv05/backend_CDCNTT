package com.example.english_app_cdcntt.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * A pending registration: the draft behind a mailed verification code. It is deliberately NOT a
 * {@link User} — the account row only appears when {@code POST /api/auth/register/confirm}
 * accepts the code, and this draft then disappears in the same transaction.
 *
 * <p>{@code email} is stored lower-cased by the service and compared case-insensitively by the
 * migration collation; {@code username} keeps the binary collation so it cannot collide with a
 * different-cased account.
 */
@Entity
@Table(name = "pending_registrations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(of = {"id", "email"})
public class PendingRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "username", nullable = false, length = 50)
    private String username;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    /** BCrypt hash of the password captured by the register form, never the plain value. */
    @Column(name = "password", nullable = false, length = 60)
    private String password;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Static factory for the first code of a draft; {@code password} is already hashed. */
    public static PendingRegistration create(String username, String name, String email, String password,
            String codeHash, Instant expiresAt, Instant now) {
        PendingRegistration pending = new PendingRegistration();
        pending.username = Objects.requireNonNull(username, "username");
        pending.name = Objects.requireNonNull(name, "name");
        pending.email = Objects.requireNonNull(email, "email");
        pending.password = Objects.requireNonNull(password, "password");
        pending.codeHash = Objects.requireNonNull(codeHash, "codeHash");
        pending.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        pending.attempts = 0;
        pending.createdAt = Objects.requireNonNull(now, "now");
        return pending;
    }

    /**
     * Overwrites an existing draft with a fresh code and the newest form values. The email owner
     * is the one who decides, so re-registering with the same email always replaces the draft.
     */
    public void refresh(String username, String name, String password, String codeHash, Instant expiresAt) {
        this.username = Objects.requireNonNull(username, "username");
        this.name = Objects.requireNonNull(name, "name");
        this.password = Objects.requireNonNull(password, "password");
        this.codeHash = Objects.requireNonNull(codeHash, "codeHash");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.attempts = 0;
    }

    /** Inclusive like {@code refresh_tokens.expiry_date}: a code expiring exactly now is dead. */
    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(Objects.requireNonNull(now, "now"));
    }

    public void incrementAttempts() {
        this.attempts = this.attempts + 1;
    }
}
