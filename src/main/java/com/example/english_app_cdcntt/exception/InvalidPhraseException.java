package com.example.english_app_cdcntt.exception;

/** §4.1 row 13 — §9.2 phrase violation from form validation or non-phrase AI answer. */
public class InvalidPhraseException extends RuntimeException {

    public static final String MESSAGE = "Đoạn văn gửi lên không hợp lệ";

    public InvalidPhraseException() {
        super(MESSAGE);
    }
}
