package com.example.english_app_cdcntt.config;

/** A secret must be accessed explicitly; diagnostics never print its value. */
public record SecretValue(String value) {
    public boolean isConfigured() {
        return value != null && !value.isBlank() && !value.contains("${");
    }

    @Override
    public String toString() {
        return "[REDACTED]";
    }
}
