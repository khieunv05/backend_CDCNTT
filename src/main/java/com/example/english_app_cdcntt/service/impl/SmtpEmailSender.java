package com.example.english_app_cdcntt.service.impl;

import com.example.english_app_cdcntt.config.MailProperties;
import com.example.english_app_cdcntt.exception.EmailDeliveryException;
import com.example.english_app_cdcntt.service.EmailPurpose;
import com.example.english_app_cdcntt.service.EmailSender;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Brevo SMTP adapter. Plain text on purpose: no HTML template, no tracking pixels, and a body
 * that tells the user what to do if they did not ask for the code.
 */
@Component
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final MailProperties properties;

    public SmtpEmailSender(JavaMailSender mailSender, MailProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void sendVerificationCode(String email, String code, EmailPurpose purpose) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.from());
        message.setTo(email);
        message.setSubject(purpose.subject());
        message.setText(body(code));
        try {
            mailSender.send(message);
        } catch (MailException e) {
            // §5.2 — the caller answers 502 and keeps the draft so a retry can issue a new code.
            throw new EmailDeliveryException(e);
        }
    }

    private static String body(String code) {
        return """
                Xin chào,

                Mã xác thực LEnglish của bạn là: %s

                Mã chỉ dùng được một lần và sẽ hết hạn sau ít phút.
                Nếu bạn không yêu cầu việc này, hãy bỏ qua email này — mã sẽ tự hết hiệu lực.

                — LEnglish
                """.formatted(code);
    }
}
