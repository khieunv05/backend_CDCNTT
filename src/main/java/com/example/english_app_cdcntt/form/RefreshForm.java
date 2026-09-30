package com.example.english_app_cdcntt.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * POST /api/auth/refresh body (§5.1: không blank, tối đa 2048 ký tự ASCII). Constraint failure
 * here answers 401 {@code Đăng nhập thất bại} (§5.1:229) — {@code GlobalExceptionHandler} maps
 * by this exact type; the structurally identical {@link LogoutForm} deliberately does not.
 */
public record RefreshForm(
        @NotBlank(message = "Refresh token không được để trống")
        @Size(max = 2048, message = "Refresh token tối đa 2048 ký tự")
        @Pattern(regexp = "[\\x00-\\x7F]*", message = "Refresh token chỉ được chứa ký tự ASCII")
        String refreshToken) {
}
