-- V4: email-based account recovery plus the two short-lived OTP tables.
--
-- Once applied, evolve this schema with a new versioned migration (same rule as V1).
-- users.email is unique and compared case-insensitively (utf8mb4_0900_ai_ci), unlike username
-- which stays binary: "Alice" and "alice" are two accounts, but one mailbox is one account.
-- pending_registrations is a registration draft, NOT an account: the users row is only created
-- when the mailed code is accepted, and the draft dies with its code.
-- Safe order: MySQL fills a NOT NULL column added to a populated table with '' (empty string),
-- which would break uk_users_email on the second row. Add it nullable, give every pre-existing
-- account an unroutable placeholder (they have no recovery address yet), then tighten the column.
ALTER TABLE users
    ADD COLUMN email VARCHAR(254) COLLATE utf8mb4_0900_ai_ci NULL AFTER name;

UPDATE users SET email = CONCAT('legacy+', id, '@legacy.invalid') WHERE email IS NULL;

ALTER TABLE users
    MODIFY COLUMN email VARCHAR(254) COLLATE utf8mb4_0900_ai_ci NOT NULL;

ALTER TABLE users
    ADD CONSTRAINT uk_users_email UNIQUE (email);

CREATE TABLE pending_registrations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    username VARCHAR(50) COLLATE utf8mb4_0900_bin NOT NULL,
    email VARCHAR(254) COLLATE utf8mb4_0900_ai_ci NOT NULL,
    password VARCHAR(60) NOT NULL,
    code_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    send_count INT NOT NULL DEFAULT 0,
    window_started_at DATETIME(6) NOT NULL,
    last_sent_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_pending_registrations_email UNIQUE (email),
    CONSTRAINT uk_pending_registrations_username UNIQUE (username),
    INDEX idx_pending_registrations_expires (expires_at),
    CONSTRAINT ck_pending_registrations_attempts CHECK (attempts >= 0),
    CONSTRAINT ck_pending_registrations_send_count CHECK (send_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE password_reset_codes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    code_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    send_count INT NOT NULL DEFAULT 0,
    window_started_at DATETIME(6) NOT NULL,
    last_sent_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_password_reset_codes_user UNIQUE (user_id),
    INDEX idx_password_reset_codes_expires (expires_at),
    CONSTRAINT fk_password_reset_codes_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_password_reset_codes_attempts CHECK (attempts >= 0),
    CONSTRAINT ck_password_reset_codes_send_count CHECK (send_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
