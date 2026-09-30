package com.example.english_app_cdcntt.config;

import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({JwtProperties.class, AiProperties.class, CleanupProperties.class})
public class ApplicationPropertiesConfig {

    @Bean
    @ConfigurationPropertiesBinding
    static SecretValueConverter secretValueConverter() {
        return new SecretValueConverter();
    }

    static final class SecretValueConverter implements Converter<String, SecretValue> {
        @Override
        public SecretValue convert(String source) {
            return new SecretValue(source);
        }
    }
}
