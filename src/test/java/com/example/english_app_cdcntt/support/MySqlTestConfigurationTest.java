package com.example.english_app_cdcntt.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MySqlTestConfigurationTest {
    @Test
    void dataSource_withTestCredentials_shouldUseOnlyDedicatedLocalDatabaseWithoutConnecting() {
        var environment = new MockEnvironment()
                .withProperty("TEST_DB_USERNAME", "lenglish_test")
                .withProperty("TEST_DB_PASSWORD", "fake-password")
                .withProperty("DB_URL", "jdbc:mysql://production/example")
                .withProperty("DB_USERNAME", "root");
        var source = (DriverManagerDataSource) new MySqlTestConfiguration().dataSource(environment);
        assertThat(source.getUrl()).isEqualTo(MySqlTestConfiguration.TEST_URL);
        assertThat(source.getUsername()).isEqualTo("lenglish_test");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:mysql://localhost:3306/production",
            "jdbc:mysql://remote:3306/lenglish_test",
            "jdbc:mysql://localhost:3307/lenglish_test",
            "jdbc:mysql://localhost:3306/lenglish_test?user=root",
            "jdbc:mysql://localhost:3306/lenglish_test"
    })
    void validateTarget_withUnexpectedUrl_shouldRejectBeforeConnecting(String url) {
        assertThatThrownBy(() -> MySqlTestConfiguration.validateTarget(url, "lenglish_test", "fake"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("TEST_DB_URL");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"root", "app_user"})
    void validateTarget_withoutDedicatedAccount_shouldReject(String username) {
        assertThatThrownBy(() -> MySqlTestConfiguration.validateTarget(MySqlTestConfiguration.TEST_URL, username, "fake"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("TEST_DB_USERNAME");
    }

    @ParameterizedTest
    @ValueSource(strings = {"spring.flyway.url", "spring.flyway.user", "spring.flyway.password"})
    void dataSource_withSeparateFlywayConnection_shouldRejectBeforeConnecting(String property) {
        var environment = new MockEnvironment()
                .withProperty("TEST_DB_USERNAME", "lenglish_test")
                .withProperty("TEST_DB_PASSWORD", "fake-password")
                .withProperty(property, "unexpected-override");
        assertThatThrownBy(() -> new MySqlTestConfiguration().dataSource(environment))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Do not override Flyway connection settings in local DB tests");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void validateTarget_withoutPassword_shouldRejectWithoutPrintingSecret(String password) {
        assertThatThrownBy(() -> MySqlTestConfiguration.validateTarget(MySqlTestConfiguration.TEST_URL, "lenglish_test", password))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("TEST_DB_PASSWORD");
    }
}
