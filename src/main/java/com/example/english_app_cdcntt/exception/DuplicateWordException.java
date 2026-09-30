package com.example.english_app_cdcntt.exception;

/** Mapped to the contract 409 body (§4.1 rows 8–9) — the message itself is part of the response. */
public class DuplicateWordException extends RuntimeException {

    public DuplicateWordException() {
        super("Từ này đã có trong sổ của bạn");
    }
}
