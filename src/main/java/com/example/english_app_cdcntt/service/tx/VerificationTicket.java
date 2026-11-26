package com.example.english_app_cdcntt.service.tx;

/**
 * What a write transaction hands back so the caller can send the mail <em>after</em> the commit:
 * the destination and the raw code. The raw code never leaves this object except into the mail.
 */
public record VerificationTicket(String email, String code) {
}
