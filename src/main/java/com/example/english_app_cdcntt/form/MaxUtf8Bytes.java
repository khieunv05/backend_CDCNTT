package com.example.english_app_cdcntt.form;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Limits the UTF-8 encoding of a string to {@code value} bytes (§5.1: password tối đa 72 byte).
 * {@code @Size} is deliberately not sufficient — it counts chars, and a 60-char Vietnamese
 * password already exceeds BCrypt's 72-byte truncation window.
 */
@Documented
@Constraint(validatedBy = Utf8ByteLengthValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxUtf8Bytes {

    String message() default "Vượt quá số byte UTF-8 tối đa";

    int value();

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
