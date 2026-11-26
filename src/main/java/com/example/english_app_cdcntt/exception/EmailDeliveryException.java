package com.example.english_app_cdcntt.exception;

/**
 * §5.2 — the mail provider rejected or timed out. The draft stays in the database, so the caller
 * can post the same endpoint again to get a fresh code; only the cause is logged (§8.2 style).
 */
public class EmailDeliveryException extends RuntimeException {

    public static final String MESSAGE = "Không gửi được email xác thực, vui lòng thử lại";

    public EmailDeliveryException(Throwable cause) {
        super(MESSAGE, cause);
    }
}
