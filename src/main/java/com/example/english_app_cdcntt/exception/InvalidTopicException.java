package com.example.english_app_cdcntt.exception;

/**
 * §4.1 row 15 — the AI judged the topic unusable for vocabulary generation (§7.1 step 2).
 * Maps to the dedicated 400 text, before the database is touched.
 */
public class InvalidTopicException extends RuntimeException {

    public static final String MESSAGE = "Chủ đề không hợp lệ, vui lòng nhập lại";

    public InvalidTopicException() {
        super(MESSAGE);
    }
}
