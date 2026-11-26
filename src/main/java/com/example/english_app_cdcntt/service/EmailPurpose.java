package com.example.english_app_cdcntt.service;

/** Which flow a mailed code belongs to; only the text shown to the user differs. */
public enum EmailPurpose {

    REGISTRATION("Xác thực email đăng ký LEnglish"),
    PASSWORD_RESET("Đặt lại mật khẩu LEnglish");

    private final String subject;

    EmailPurpose(String subject) {
        this.subject = subject;
    }

    public String subject() {
        return subject;
    }
}
