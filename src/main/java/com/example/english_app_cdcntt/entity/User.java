package com.example.english_app_cdcntt.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * An application account. {@code username} is compared with the binary collation from the migration
 * so {@code Alice} and {@code alice} are distinct accounts; {@code email} uses the case-insensitive
 * collation because one mailbox is one account.
 *
 * <p>{@code password} holds a BCrypt hash (60 characters), never a plain password. {@code email} is
 * verified by construction: the row is only created after the mailed code was accepted, which is
 * also why it is the sole channel for password recovery.
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(of = {"id", "username"})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    @Column(name = "id")
    private Long id;

    @Column(name = "username", nullable = false, length = 50)
    private String username;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "password", nullable = false, length = 60)
    private String password;

    protected User(String username, String name, String email, String password) {
        this.username = Objects.requireNonNull(username, "username");
        this.name = Objects.requireNonNull(name, "name");
        this.email = Objects.requireNonNull(email, "email");
        this.password = Objects.requireNonNull(password, "password");
    }

    /** Static factory; {@code password} must already be a BCrypt hash and {@code email} verified. */
    public static User create(String username, String name, String email, String password) {
        return new User(username, name, email, password);
    }

    /** Display name only — changing it never affects login or any token. */
    public void changeName(String newName) {
        this.name = Objects.requireNonNull(newName, "newName");
    }

    public void changePassword(String newPassword) {
        this.password = Objects.requireNonNull(newPassword, "newPassword");
    }
}
