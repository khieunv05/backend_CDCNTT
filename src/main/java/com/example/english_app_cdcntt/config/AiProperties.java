package com.example.english_app_cdcntt.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties("app.ai")
@Validated
public record AiProperties(
        @NotNull URI baseUrl,
        @NotNull SecretValue apiKey,
        @NotBlank String model,
        @NotNull @DefaultValue("10s") Duration connectTimeout,
        @NotNull @DefaultValue("30s") Duration readTimeout) {

    @AssertTrue(message = "AI base URL must be absolute HTTPS without credentials, query or fragment")
    public boolean isBaseUrlValid() {
        return baseUrl != null && "https".equalsIgnoreCase(baseUrl.getScheme())
                && baseUrl.getHost() != null && baseUrl.getUserInfo() == null
                && baseUrl.getQuery() == null && baseUrl.getFragment() == null;
    }

    @AssertTrue(message = "AI API key must be supplied externally")
    public boolean isApiKeyValid() {
        return apiKey != null && apiKey.isConfigured();
    }

    @AssertTrue(message = "AI model must be resolved from configuration")
    public boolean isModelValid() {
        return model != null && !model.contains("${");
    }

    @AssertTrue(message = "AI timeouts must be positive")
    public boolean isTimeoutsValid() {
        return connectTimeout != null && connectTimeout.isPositive()
                && readTimeout != null && readTimeout.isPositive();
    }
}
