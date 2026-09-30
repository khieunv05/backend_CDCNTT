-- MySQL 8.0.43. Application supplies UTC timestamps; no database clock defaults.
-- Once applied, evolve this schema with a new versioned migration.
CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(50) COLLATE utf8mb4_0900_bin NOT NULL,
    password VARCHAR(60) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_username UNIQUE (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE words (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    english VARCHAR(255) COLLATE utf8mb4_0900_bin NOT NULL,
    level VARCHAR(2) NULL,
    review_count INT NOT NULL DEFAULT 0,
    next_review DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_words_user_english UNIQUE (user_id, english),
    INDEX idx_words_user_next_review (user_id, next_review),
    CONSTRAINT fk_words_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_words_review_count CHECK (review_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE word_values (
    id BIGINT NOT NULL AUTO_INCREMENT,
    word_id BIGINT NOT NULL,
    vietnamese VARCHAR(1000) NOT NULL,
    example VARCHAR(2000) NULL,
    example_translation VARCHAR(2000) NULL,
    pronunciation VARCHAR(255) NULL,
    part_of_speech VARCHAR(20) NULL,
    PRIMARY KEY (id),
    INDEX idx_word_values_word (word_id),
    CONSTRAINT fk_word_values_word FOREIGN KEY (word_id) REFERENCES words (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE word_cache (
    id BIGINT NOT NULL AUTO_INCREMENT,
    english VARCHAR(255) COLLATE utf8mb4_0900_bin NOT NULL,
    level VARCHAR(2) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_word_cache_english UNIQUE (english)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE word_cache_values (
    id BIGINT NOT NULL AUTO_INCREMENT,
    word_cache_id BIGINT NOT NULL,
    vietnamese VARCHAR(1000) NOT NULL,
    example VARCHAR(2000) NOT NULL,
    example_translation VARCHAR(2000) NOT NULL,
    pronunciation VARCHAR(255) NOT NULL,
    part_of_speech VARCHAR(20) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_word_cache_values_cache (word_cache_id),
    CONSTRAINT fk_word_cache_values_cache FOREIGN KEY (word_cache_id) REFERENCES word_cache (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE phrases (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    text TEXT NOT NULL,
    corrected_text TEXT NOT NULL,
    score INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_phrases_user (user_id),
    CONSTRAINT fk_phrases_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_phrases_score CHECK (score BETWEEN 0 AND 10)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE grammar_errors (
    id BIGINT NOT NULL AUTO_INCREMENT,
    phrase_id BIGINT NOT NULL,
    incorrect TEXT NOT NULL,
    correction TEXT NOT NULL,
    explanation TEXT NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_grammar_errors_phrase (phrase_id),
    CONSTRAINT fk_grammar_errors_phrase FOREIGN KEY (phrase_id) REFERENCES phrases (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE refresh_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token VARCHAR(2048) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    expiry_date DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_refresh_tokens_token UNIQUE (token),
    INDEX idx_refresh_tokens_user (user_id),
    INDEX idx_refresh_tokens_expiry (expiry_date),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
