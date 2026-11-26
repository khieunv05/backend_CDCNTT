package com.example.english_app_cdcntt.exception;

/** §5.2 — resend cooldown or the hourly send quota was hit on a path that may answer 429. */
public class TooManyRequestsException extends RuntimeException {

    public static final String MESSAGE = "Bạn đã yêu cầu quá nhiều lần, vui lòng thử lại sau";

    public TooManyRequestsException() {
        super(MESSAGE);
    }
}
