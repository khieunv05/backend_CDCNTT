package com.example.english_app_cdcntt.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * POST /api/auth/logout body — same field and rules as {@link RefreshForm}, but a distinct type
 * because §5.1:229 requires logout form failures to answer the generic 400 validation body
 * while refresh failures answer 401. The advice switches on the parameter type.
 */
public record LogoutForm(
        @NotBlank(message = "Refresh token không được để trống")
        @Size(max = 2048, message = "Refresh token tối đa 2048 ký tự")
        @Pattern(regexp = "[\\x00-\\x7F]*", message = "Refresh token chỉ được chứa ký tự ASCII")
        String refreshToken) {
}
