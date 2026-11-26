package com.example.english_app_cdcntt.form;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST /api/auth/forgot-password body. Email only by design: the account's email is the sole
 * recovery channel, so a username is never accepted here.
 */
public record ForgotPasswordForm(
        @NotBlank(message = "Email không được để trống")
        @Email(message = "Email không hợp lệ")
        @Size(max = 254, message = "Email không được vượt quá 254 ký tự")
        String email) {
}
