package com.example.english_app_cdcntt.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * §4.1 row 16 — body of POST /api/words/generate-topic/confirm (act-20 step 2): the English
 * words the user picked on the proposal screen. Bounds mirror the proposal flow: at least one
 * word, at most 20 (the AI never proposes more than that), each within the §2 stored shape
 * limits; the pattern is enforced by the service so a bad value keeps the §7 contract
 * (400 "Từ hoặc cụm từ gửi lên không phải một từ tiếng Anh hợp lệ").
 */
public record TopicConfirmForm(
        @NotEmpty(message = "Danh sách từ không được để trống")
        @Size(max = 20, message = "Chỉ được thêm tối đa 20 từ mỗi lần")
        List<@NotBlank(message = "Từ không được để trống") @Size(max = 255, message = "Từ tối đa 255 ký tự") String> words) {
}
