package com.example.english_app_cdcntt.exception;

import org.springframework.security.access.AccessDeniedException;

/**
 * Thrown when a word/phrase/review is missing or belongs to another user: both cases answer 403
 * with the same message (§4.1, §13.2 — there is no 404 business branch and no ownership leak). It
 * extends {@link AccessDeniedException} because it is a denial, but carries the contract message
 * so {@code GlobalExceptionHandler} can answer with the per-operation text; framework denials
 * without a message keep the generic one.
 */
public class OwnershipDeniedException extends AccessDeniedException {

    private static final long serialVersionUID = 1L;

    public OwnershipDeniedException(String message) {
        super(message);
    }

    /** §4.1 row 9 — missing or foreign word id on PUT. */
    public static OwnershipDeniedException updateWord() {
        return new OwnershipDeniedException("Không có quyền sửa từ này");
    }

    /** §4.1 row 10 — missing or foreign word id on DELETE. */
    public static OwnershipDeniedException deleteWord() {
        return new OwnershipDeniedException("Không có quyền xóa từ này");
    }
}
