package com.example.english_app_cdcntt.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * §4.1 row 15 — body of POST /api/words/generate-topic. The topic is free text (Vietnamese or
 * English); validity is judged by the AI in the service layer, here only presence and length
 * are enforced. The topic is never stored, so a char cap (not a byte cap) is the right guard.
 */
public record TopicForm(

        @NotBlank(message = "Chủ đề không được để trống")
        @Size(max = 255, message = "Chủ đề tối đa 255 ký tự")
        String topic) {
}
