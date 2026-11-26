package com.example.english_app_cdcntt.exception;

/** §5.2 — the email already belongs to an account, answered as 409. */
public class DuplicateEmailException extends RuntimeException {

    public static final String MESSAGE = "Email này đã được sử dụng";

    public DuplicateEmailException() {
        super(MESSAGE);
    }
}
