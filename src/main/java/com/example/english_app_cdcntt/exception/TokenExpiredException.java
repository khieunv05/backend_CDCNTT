package com.example.english_app_cdcntt.exception;

/**
 * Mapped to the contract 401 {@code {"message":"Phiên đăng nhập hết hạn"}} — thrown only AFTER
 * the rotation transaction that deleted the expired row has committed (§5.2:240), so the deletion
 * is never rolled back by the error response.
 */
public class TokenExpiredException extends RuntimeException {

    public TokenExpiredException() {
        super("Phiên đăng nhập hết hạn");
    }
}
