package com.example.english_app_cdcntt.exception;

/**
 * Mapped to the contract 400 {@code {"message":"Mật khẩu mới phải khác mật khẩu hiện tại"}}.
 * Rejected explicitly so a no-op "change" can never look like a success.
 */
public class PasswordReuseException extends RuntimeException {

    public PasswordReuseException() {
        super("Mật khẩu mới phải khác mật khẩu hiện tại");
    }
}
