package com.example.english_app_cdcntt.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * §4.1 row 16 — body of POST /api/words/generate-topic/confirm (act-20 step 2): the full
 * {@link WordForm} payloads (english, level, values) for the words the user picked on the
 * proposal screen, exactly like the POST /api/words body. At least one word, at most 20 per
 * call; each {@code english} must still satisfy the §2 stored shape or the service keeps the
 * §7 contract (400 "Từ hoặc cụm từ gửi lên không phải một từ tiếng Anh hợp lệ"). Per the user
 * decision of 2026-10-05, confirm trusts this payload: it does NOT consult word_cache and
 * never calls the AI — the notebook rows are built straight from these forms.
 */
public record TopicConfirmForm(
        @NotEmpty(message = "Danh sách từ không được để trống")
        @Size(max = 20, message = "Chỉ được thêm tối đa 20 từ mỗi lần")
        List<@NotNull(message = "Từ không được để trống") @Valid WordForm> words) {
}
