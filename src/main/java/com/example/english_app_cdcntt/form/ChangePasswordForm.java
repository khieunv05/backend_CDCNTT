package com.example.english_app_cdcntt.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST /api/auth/change-password body. The new password follows the register rules (§5.1): at
 * least 8 characters and at most 72 UTF-8 bytes, because BCrypt silently truncates past 72.
 * The current password has no length floor: it only has to match the stored hash.
 */
public record ChangePasswordForm(
        @NotBlank(message = "Mật khẩu hiện tại không được để trống")
        @MaxUtf8Bytes(value = 72, message = "Mật khẩu không được vượt quá 72 byte")
        String currentPassword,

        @NotBlank(message = "Mật khẩu mới không được để trống")
        @Size(min = 8, message = "Mật khẩu mới phải có ít nhất 8 ký tự")
        @MaxUtf8Bytes(value = 72, message = "Mật khẩu không được vượt quá 72 byte")
        String newPassword) {
}
