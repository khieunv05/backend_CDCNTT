package com.example.english_app_cdcntt.exception;

/**
 * §9.2 — mapped to 400 with its own message: the input of GET /api/words/generate is not a
 * single English dictionary word (bad form, wrong shape after normalization, or the LLM answered
 * {@code validWord=false}).
 */
public class InvalidWordException extends RuntimeException {

    public InvalidWordException() {
        super("Từ hoặc cụm từ gửi lên không phải một từ tiếng Anh hợp lệ");
    }
}
