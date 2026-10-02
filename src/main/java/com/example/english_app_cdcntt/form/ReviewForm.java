package com.example.english_app_cdcntt.form;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * §4.1 row 11 body — §6.3:291: the raw list is capped at 500 entries BEFORE distinct; ids must be
 * positive and non-null. Duplicates are allowed in the form and deduplicated by the service.
 */
public record ReviewForm(
        @NotEmpty @Size(max = 500) List<@NotNull @Positive Long> wordIds) {
}
