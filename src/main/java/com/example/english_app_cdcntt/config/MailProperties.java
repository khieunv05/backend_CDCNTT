package com.example.english_app_cdcntt.config;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** The envelope sender shown in every code mail; only this value moves when a domain is verified. */
@ConfigurationProperties("app.mail")
@Validated
public record MailProperties(@NotBlank @Email String from) {
}
