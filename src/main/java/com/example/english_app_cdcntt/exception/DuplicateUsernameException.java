package com.example.english_app_cdcntt.exception;

/** Mapped to the contract 409 response — the message itself is part of the response body (§4). */
public class DuplicateUsernameException extends RuntimeException {

    public DuplicateUsernameException() {
        super("Tên đăng nhập đã tồn tại");
    }
}
