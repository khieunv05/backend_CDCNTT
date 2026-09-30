package com.example.english_app_cdcntt.form;

import com.example.english_app_cdcntt.enums.PartOfSpeech;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * One meaning inside a create/update word body (§6.1). Limits mirror the {@code word_values}
 * columns (§2.2). {@code id} is only meaningful on PUT; a positive id must reference a meaning
 * that already belongs to the word being updated (§6.2).
 */
public record WordValueForm(
        @Positive(message = "ID nghĩa phải là số dương")
        Long id,

        @NotBlank(message = "Nghĩa tiếng Việt không được để trống")
        @Size(max = 1000, message = "Nghĩa tiếng Việt tối đa 1000 ký tự")
        String vietnamese,

        @Size(max = 2000, message = "Ví dụ tối đa 2000 ký tự")
        String example,

        @Size(max = 2000, message = "Dịch ví dụ tối đa 2000 ký tự")
        String exampleTranslation,

        @Size(max = 255, message = "Phiên âm tối đa 255 ký tự")
        String pronunciation,

        PartOfSpeech partOfSpeech) {
}
