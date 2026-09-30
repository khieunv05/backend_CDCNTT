package com.example.english_app_cdcntt.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

/** Local-only test datasource; validate the target before Flyway can use it. */
@TestConfiguration(proxyBeanMethods = false)
public class MySqlTestConfiguration {
    public static final String TEST_URL = "jdbc:mysql://localhost:3306/lenglish_test"
            + "?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true";

    @Bean
    DataSource dataSource(Environment environment) {
        String url = environment.getProperty("TEST_DB_URL", TEST_URL);
        String username = environment.getProperty("TEST_DB_USERNAME");
        String password = environment.getProperty("TEST_DB_PASSWORD");
        validateTarget(url, username, password);
        if (environment.containsProperty("spring.flyway.url")
                || environment.containsProperty("spring.flyway.user")
                || environment.containsProperty("spring.flyway.password")) {
            throw new IllegalArgumentException("Do not override Flyway connection settings in local DB tests");
        }
        return new DriverManagerDataSource(url, username, password);
    }

    static void validateTarget(String url, String username, String password) {
        if (!TEST_URL.equals(url)) {
            throw new IllegalArgumentException("TEST_DB_URL must use the documented localhost:3306/lenglish_test URL with UTC parameters");
        }
        if (!"lenglish_test".equals(username)) {
            throw new IllegalArgumentException("TEST_DB_USERNAME must be the dedicated lenglish_test account (not root)");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Set TEST_DB_PASSWORD locally before running integration tests");
        }
    }
}
