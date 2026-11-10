package com.example.english_app_cdcntt.exception;

/**
 * Mapped to the contract 400 {@code {"message":"Mật khẩu hiện tại không đúng"}}. Deliberately
 * distinct from {@link InvalidCredentialsException}: the caller already owns a valid access
 * token, so a wrong current password is a request error, not an authentication failure.
 */
public class WrongPasswordException extends RuntimeException {

    public WrongPasswordException() {
        super("Mật khẩu hiện tại không đúng");
    }
}
