package com.example.english_app_cdcntt.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** §4.1 row 13 — paragraph to grade, 10–5000 characters (§9.2). */
public record PhraseForm(
        @NotBlank @Size(min = 10, max = 5000) String text) {
}
