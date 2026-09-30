package com.example.english_app_cdcntt.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * POST /api/auth/login body — same shape and rules as register (§5.1), but any constraint
 * failure here answers the credentials 401 {@code Đăng nhập thất bại} without revealing which
 * field failed (§5.1:228); {@code GlobalExceptionHandler} maps by this exact type.
 */
public record LoginForm(
        @NotBlank(message = "Tên đăng nhập không được để trống")
        @Size(min = 3, max = 50, message = "Tên đăng nhập phải từ 3 đến 50 ký tự")
        @Pattern(regexp = "[A-Za-z0-9_.]+",
                message = "Tên đăng nhập chỉ được chứa chữ cái, số, dấu chấm và gạch dưới")
        String username,

        @NotBlank(message = "Mật khẩu không được để trống")
        @Size(min = 8, message = "Mật khẩu phải có ít nhất 8 ký tự")
        @MaxUtf8Bytes(value = 72, message = "Mật khẩu không được vượt quá 72 byte")
        String password) {
}
