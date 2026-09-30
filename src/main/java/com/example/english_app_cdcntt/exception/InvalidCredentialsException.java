package com.example.english_app_cdcntt.exception;

/**
 * Mapped to the contract 401 {@code {"message":"Đăng nhập thất bại"}} — thrown for bad login
 * credentials as well as invalid/constraint-violating refresh requests (§4, §9.2); the two cases
 * are deliberately indistinguishable so no account state leaks.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Đăng nhập thất bại");
    }
}
