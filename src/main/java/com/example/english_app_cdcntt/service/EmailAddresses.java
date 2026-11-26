package com.example.english_app_cdcntt.service;

import java.util.Locale;

/** One normalisation for every route that accepts an email, so lookups and writes agree. */
public final class EmailAddresses {

    private EmailAddresses() {
    }

    /** Trims and lower-cases: the column collation is ai_ci, but the row should read the same. */
    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
