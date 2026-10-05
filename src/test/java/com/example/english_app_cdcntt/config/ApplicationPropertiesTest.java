package com.example.english_app_cdcntt.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ClassPathResource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import com.example.english_app_cdcntt.repository.RefreshTokenRepository;
import com.example.english_app_cdcntt.service.TokenCleanupService;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationPropertiesTest {
    // Public test-only material, never a production credential.
    private static final String SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private static final String API_KEY = "fake-test-api-key";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ApplicationPropertiesConfig.class, ClockConfig.class);

    private ApplicationContextRunner validRunner() {
        return runner.withPropertyValues(
                "app.jwt.secret=" + SECRET,
                "app.ai.base-url=https://api.example.test/v1",
                "app.ai.api-key=" + API_KEY,
                "app.ai.model=test-model");
    }

    @Test
    void binding_withRequiredSettings_shouldApplyDefaultsAndUtcClock() {
        validRunner().run(context -> {
            assertThat(context).hasNotFailed();
            JwtProperties jwt = context.getBean(JwtProperties.class);
            assertThat(jwt.secret().value()).isEqualTo(SECRET);
            assertThat(jwt.accessTokenExpiration()).isEqualTo(Duration.ofMinutes(30));
            assertThat(jwt.refreshTokenExpiration()).isEqualTo(Duration.ofDays(7));
            AiProperties ai = context.getBean(AiProperties.class);
            assertThat(ai.connectTimeout()).isEqualTo(Duration.ofSeconds(10));
            assertThat(ai.readTimeout()).isEqualTo(Duration.ofSeconds(30));
            assertThat(ai.apiKey().value()).isEqualTo(API_KEY);
            CleanupProperties cleanup = context.getBean(CleanupProperties.class);
            assertThat(cleanup.enabled()).isTrue();
            assertThat(cleanup.cron()).isEqualTo("0 0 3 * * *");
            assertThat(cleanup.zone()).isEqualTo("UTC");
            assertThat(context).hasSingleBean(Clock.class);
            assertThat(context.getBean(Clock.class).getZone()).isEqualTo(ZoneOffset.UTC);
            assertThat(jwt.toString()).doesNotContain(SECRET).contains("[REDACTED]");
            assertThat(ai.toString()).doesNotContain(API_KEY).contains("[REDACTED]");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "app.jwt.secret=", "app.jwt.secret=not-base64!", "app.jwt.secret=c2hvcnQ=",
            "app.jwt.secret=${MISSING_SECRET}",
            "app.jwt.access-token-expiration=0s", "app.jwt.access-token-expiration=-1m",
            "app.jwt.refresh-token-expiration=0d", "app.jwt.refresh-token-expiration=-1d",
            "app.ai.api-key=", "app.ai.api-key=${MISSING_KEY}",
            "app.ai.model=", "app.ai.model=${MISSING_MODEL}",
            "app.ai.base-url=/relative", "app.ai.base-url=http://api.example.test",
            "app.ai.base-url=https://user:password@api.example.test",
            "app.ai.base-url=https://api.example.test?key=secret",
            "app.ai.base-url=https://api.example.test#fragment",
            "app.ai.connect-timeout=0s", "app.ai.connect-timeout=-1s",
            "app.ai.read-timeout=0s", "app.ai.read-timeout=-1s",
            "app.cleanup.cron=invalid", "app.cleanup.zone=invalid-zone"
    })
    void binding_withInvalidSetting_shouldFailStartup(String setting) {
        validRunner().withPropertyValues(setting).run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"app.jwt.secret", "app.ai.base-url", "app.ai.api-key", "app.ai.model"})
    void binding_withMissingRequiredSetting_shouldFailStartup(String missing) {
        Map<String, String> settings = new HashMap<>(Map.of(
                "app.jwt.secret", SECRET,
                "app.ai.base-url", "https://api.example.test/v1",
                "app.ai.api-key", API_KEY,
                "app.ai.model", "test-model"));
        settings.remove(missing);
        runner.withPropertyValues(settings.entrySet().stream()
                        .map(entry -> entry.getKey() + "=" + entry.getValue()).toArray(String[]::new))
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void binding_withSharedTestProfile_shouldNotNeedPersonalSecrets() {
        runner.withInitializer(context -> {
            var sources = context.getEnvironment().getPropertySources();
            sources.remove("systemEnvironment");
            sources.remove("systemProperties");
            try {
                var loader = new YamlPropertySourceLoader();
                loader.load("test-profile", new ClassPathResource("application-test.yml"))
                        .forEach(sources::addLast);
                loader.load("application", new ClassPathResource("application.yml"))
                        .forEach(sources::addLast);
            } catch (java.io.IOException exception) {
                throw new IllegalStateException(exception);
            }
        }).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(JwtProperties.class).secret().value()).isEqualTo(SECRET);
            assertThat(context.getBean(AiProperties.class).apiKey().value()).isEqualTo(API_KEY);
            assertThat(context.getBean(CleanupProperties.class).enabled()).isFalse();
            var environment = context.getEnvironment();
            assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
            assertThat(environment.getProperty("spring.flyway.enabled", Boolean.class)).isTrue();
            assertThat(environment.getProperty("spring.flyway.clean-disabled", Boolean.class)).isTrue();
            assertThat(environment.getProperty("spring.flyway.baseline-on-migrate", Boolean.class)).isFalse();
        });
    }

    @Test
    void binding_withPackagedYamlAndEnvironment_shouldResolveSecretsAndOverrides() {
        runner.withInitializer(context -> {
            var sources = context.getEnvironment().getPropertySources();
            // Isolate this test from developer machine credentials/properties.
            sources.remove("systemEnvironment");
            sources.remove("systemProperties");
            sources.addFirst(new SystemEnvironmentPropertySource("systemEnvironment", Map.of(
                    "JWT_SECRET", SECRET,
                    "AI_BASE_URL", "https://api.example.test/v1",
                    "AI_API_KEY", API_KEY,
                    "AI_MODEL", "environment-model",
                    "APP_JWT_ACCESSTOKENEXPIRATION", "45m",
                    "APP_AI_READTIMEOUT", "12s",
                    "APP_CLEANUP_ENABLED", "false",
                    "DB_URL", "jdbc:mysql://localhost:3306/test?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true",
                    "DB_USERNAME", "test-user",
                    "DB_PASSWORD", "test-password")));
            try {
                new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"))
                        .forEach(sources::addLast);
            } catch (java.io.IOException exception) {
                throw new IllegalStateException(exception);
            }
        }).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(JwtProperties.class).secret().value()).isEqualTo(SECRET);
            assertThat(context.getBean(JwtProperties.class).accessTokenExpiration()).isEqualTo(Duration.ofMinutes(45));
            assertThat(context.getBean(AiProperties.class).model()).isEqualTo("environment-model");
            assertThat(context.getBean(AiProperties.class).readTimeout()).isEqualTo(Duration.ofSeconds(12));
            assertThat(context.getBean(CleanupProperties.class).enabled()).isFalse();
            var environment = context.getEnvironment();
            assertThat(environment.getProperty("spring.datasource.username")).isEqualTo("test-user");
            assertThat(environment.getProperty("spring.datasource.password")).isEqualTo("test-password");
            assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
            assertThat(environment.getProperty("spring.jpa.open-in-view", Boolean.class)).isFalse();
            assertThat(environment.getProperty("spring.jpa.properties.hibernate.jdbc.time_zone")).isEqualTo("UTC");
            assertThat(environment.getProperty("spring.flyway.enabled", Boolean.class)).isTrue();
            assertThat(environment.getProperty("spring.flyway.baseline-on-migrate", Boolean.class)).isFalse();
        });
    }

    @Test
    void cleanupEnabledFlag_shouldGateTokenCleanupServiceBean() {
        ApplicationContextRunner serviceRunner = new ApplicationContextRunner()
                .withUserConfiguration(TokenCleanupService.class)
                .withBean("clock", Clock.class, () -> Clock.fixed(Instant.EPOCH, ZoneOffset.UTC))
                .withBean(RefreshTokenRepository.class, () -> Mockito.mock(RefreshTokenRepository.class));

        serviceRunner.withPropertyValues("app.cleanup.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(TokenCleanupService.class));
        // Absent flag keeps the documented default (true) — bean is registered.
        serviceRunner.run(context -> assertThat(context).hasSingleBean(TokenCleanupService.class));
    }
}
