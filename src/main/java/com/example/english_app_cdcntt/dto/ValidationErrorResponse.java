package com.example.english_app_cdcntt.dto;

import java.util.List;

/** 400 body of §4: {@code {"message":"Thông tin không hợp lệ","details":[{"field","message"}...]}}. */
public record ValidationErrorResponse(String message, List<FieldErrorDetail> details) {
}
