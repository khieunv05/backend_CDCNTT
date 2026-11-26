package com.example.english_app_cdcntt.dto;

/**
 * Public profile of the authenticated account (GET/PUT {@code /api/users/me}). Never contains the
 * password hash. {@code username} is the login key and does not change; {@code name} is the display
 * name the user can edit.
 */
public record UserProfileDto(Long id, String username, String name) {
}
