package com.example.english_app_cdcntt.exception;

/**
 * One message for every failure mode — wrong code, expired code, no draft at all — so the endpoint
 * never tells a caller whether an email is registered (§5.2 enumeration rule).
 */
public class InvalidVerificationCodeException extends RuntimeException {

    public static final String MESSAGE = "Mã xác thực không đúng hoặc đã hết hạn";

    public InvalidVerificationCodeException() {
        super(MESSAGE);
    }
}
