package com.example.english_app_cdcntt.form;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** POST /api/auth/reset-password body: the mailed code plus the new password. */
public record ResetPasswordForm(
        @NotBlank(message = "Email không được để trống")
        @Email(message = "Email không hợp lệ")
        @Size(max = 254, message = "Email không được vượt quá 254 ký tự")
        String email,

        @NotBlank(message = "Mã xác thực không được để trống")
        @Pattern(regexp = "[0-9]{6}", message = "Mã xác thực phải gồm 6 chữ số")
        String code,

        @NotBlank(message = "Mật khẩu không được để trống")
        @Size(min = 8, message = "Mật khẩu phải có ít nhất 8 ký tự")
        @MaxUtf8Bytes(value = 72, message = "Mật khẩu không được vượt quá 72 byte")
        String newPassword) {
}
