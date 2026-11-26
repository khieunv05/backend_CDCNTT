package com.example.english_app_cdcntt.service;

/**
 * Outbound port for the code mails. The service layer only decides <em>when</em> to send; the
 * adapter owns SMTP and turns provider failures into {@code EmailDeliveryException}.
 */
public interface EmailSender {

    void sendVerificationCode(String email, String code, EmailPurpose purpose);
}
