package com.example.english_app_cdcntt.exception;

import lombok.Getter;

/**
 * §8.2 — any AI infrastructure failure (network, timeout, provider 4xx/5xx, wrong schema).
 * The client always sees the same fixed §4.1 text; the technical detail stays server-side
 * (for the warn log) and never contains secrets or user content.
 */
@Getter
public class AiServiceException extends RuntimeException {

    public static final String MESSAGE = "Dịch vụ AI tạm thời không khả dụng";

    private final String detail;

    public AiServiceException(String detail) {
        super(MESSAGE);
        this.detail = detail;
    }

    public AiServiceException(String detail, Throwable cause) {
        super(MESSAGE, cause);
        this.detail = detail;
    }

    public AiServiceException(Throwable cause) {
        super(MESSAGE, cause);
        this.detail = cause == null ? null : cause.getClass().getSimpleName();
    }
}
