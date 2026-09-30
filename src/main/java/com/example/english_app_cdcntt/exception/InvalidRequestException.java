package com.example.english_app_cdcntt.exception;

/**
 * A §6.2 rule that Bean Validation cannot express: a meaning id sent on create, or a meaning id
 * that is unknown, foreign or repeated on update. Mapped to the generic 400 validation body with
 * one {@code details[]} entry; unknown, foreign and repeated share the same message so the
 * response never reveals who owns that id (§6.2:283).
 */
public class InvalidRequestException extends RuntimeException {

    private final String field;

    public InvalidRequestException(String field, String message) {
        super(message);
        this.field = field;
    }

    /** POST /api/words must not carry meaning ids — those meanings do not exist yet (§6.2:280). */
    public static InvalidRequestException meaningIdOnCreate() {
        return new InvalidRequestException("values[].id", "Không được gửi ID nghĩa khi thêm từ");
    }

    /** PUT /api/words/{id}: the id is not a meaning of this word, or the form repeats it (§6.2:283). */
    public static InvalidRequestException unknownMeaningId() {
        return new InvalidRequestException("values[].id", "ID nghĩa không hợp lệ");
    }

    public String field() {
        return field;
    }
}
