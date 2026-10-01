package com.example.english_app_cdcntt.dto;

/** §4.1 row 12 — one corrected-error row of a graded paragraph, errors listed in record order. */
public record GrammarErrorDto(String incorrect, String correction, String explanation) {
}
