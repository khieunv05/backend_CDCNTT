package com.example.english_app_cdcntt.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * §4.1 row 13 — paragraph to grade. {@code title} is a short user-chosen label for the notebook
 * (max 255): it is stored verbatim and never sent to the AI; only {@code text} is graded (§9.2).
 */
public record PhraseForm(
        @NotBlank @Size(max = 255) String title,
        @NotBlank @Size(min = 10, max = 5000) String text) {
}
