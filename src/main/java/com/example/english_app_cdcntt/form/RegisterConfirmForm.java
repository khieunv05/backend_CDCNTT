package com.example.english_app_cdcntt.form;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** POST /api/auth/register/confirm body: the email plus the six-digit code it received. */
public record RegisterConfirmForm(
        @NotBlank(message = "Email không được để trống")
        @Email(message = "Email không hợp lệ")
        @Size(max = 254, message = "Email không được vượt quá 254 ký tự")
        String email,

        @NotBlank(message = "Mã xác thực không được để trống")
        @Pattern(regexp = "[0-9]{6}", message = "Mã xác thực phải gồm 6 chữ số")
        String code) {
}
