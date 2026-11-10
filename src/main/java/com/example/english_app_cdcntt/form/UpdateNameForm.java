package com.example.english_app_cdcntt.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * PUT /api/users/me body: the new display name. Whitespace-only is rejected by {@code @NotBlank};
 * the service trims surrounding spaces before saving.
 */
public record UpdateNameForm(
        @NotBlank(message = "Tên không được để trống")
        @Size(max = 100, message = "Tên không được vượt quá 100 ký tự")
        String name) {
}
