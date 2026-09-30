package com.example.english_app_cdcntt.form;

import com.example.english_app_cdcntt.enums.Level;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * POST/PUT /api/words body (§6.1). It deliberately has no {@code reviewCount}, {@code nextReview}
 * or {@code userId}: the SRS state and the owner are never accepted from the client (§6.1:274).
 * {@code english} is normalized with {@code strip().toLowerCase(Locale.ROOT)} before the duplicate
 * check and before storage (§2.1:58); a whitespace-only value is already rejected by
 * {@code @NotBlank}, so the normalized key can never be empty.
 */
public record WordForm(
        @NotBlank(message = "Từ không được để trống")
        @Size(max = 255, message = "Từ tối đa 255 ký tự")
        String english,

        /** {@code null} while the CEFR level has not been determined yet (§2.2). */
        Level level,

        @NotEmpty(message = "Từ phải có ít nhất 1 nghĩa")
        @Size(max = 20, message = "Từ chỉ được có tối đa 20 nghĩa")
        List<@NotNull(message = "Nghĩa không được để trống") @Valid WordValueForm> values) {
}
