package com.example.english_app_cdcntt.support;

import com.example.english_app_cdcntt.service.EmailPurpose;
import com.example.english_app_cdcntt.service.EmailSender;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Stands in for Brevo in every Spring test context: it records what would have been mailed instead
 * of opening an SMTP connection, so an integration test can read the code back out of the "mail".
 */
public class RecordingEmailSender implements EmailSender {

    /** One captured mail; the code is the raw code, not its hash. */
    public record SentCode(String email, String code, EmailPurpose purpose) {
    }

    private final List<SentCode> sent = new CopyOnWriteArrayList<>();

    @Override
    public void sendVerificationCode(String email, String code, EmailPurpose purpose) {
        sent.add(new SentCode(email, code, purpose));
    }

    public List<SentCode> sent() {
        return List.copyOf(sent);
    }

    /** Newest code mailed to this address for this purpose. */
    public Optional<String> latestCode(String email, EmailPurpose purpose) {
        return sent.stream()
                .filter(mail -> mail.email().equals(email) && mail.purpose() == purpose)
                .reduce((first, second) -> second)
                .map(SentCode::code);
    }

    public void clear() {
        sent.clear();
    }
}
