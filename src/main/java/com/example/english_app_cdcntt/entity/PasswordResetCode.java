package com.example.english_app_cdcntt.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * The one live password-reset code of an account ({@code UNIQUE (user_id)}). A successful reset
 * deletes the row, so a code can never be replayed; a new code replaces the previous row.
 */
@Entity
@Table(name = "password_reset_codes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(of = {"id", "expiresAt"})
public class PasswordResetCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static PasswordResetCode create(User user, String codeHash, Instant expiresAt, Instant now) {
        PasswordResetCode code = new PasswordResetCode();
        code.user = Objects.requireNonNull(user, "user");
        code.codeHash = Objects.requireNonNull(codeHash, "codeHash");
        code.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        code.attempts = 0;
        code.createdAt = Objects.requireNonNull(now, "now");
        return code;
    }

    public void refresh(String codeHash, Instant expiresAt) {
        this.codeHash = Objects.requireNonNull(codeHash, "codeHash");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.attempts = 0;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(Objects.requireNonNull(now, "now"));
    }

    public void incrementAttempts() {
        this.attempts = this.attempts + 1;
    }
}
