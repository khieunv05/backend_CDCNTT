package com.example.english_app_cdcntt.dto;

/** Generic error body of §4/§9.2: {@code {"message":"..."}} — the only error shape without field details. */
public record ErrorResponse(String message) {
}
