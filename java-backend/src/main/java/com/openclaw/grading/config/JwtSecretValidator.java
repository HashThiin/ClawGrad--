package com.openclaw.grading.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class JwtSecretValidator {
    private static final int MIN_SECRET_LENGTH = 32;
    private static final String DEFAULT_SECRET = "clawgrad-local-development-secret-change-me";

    private final String secret;
    private final boolean requireStrongSecret;
    private final Environment environment;

    public JwtSecretValidator(@Value("${app.security.jwt.secret}") String secret,
                              @Value("${app.security.jwt.require-strong-secret:false}") boolean requireStrongSecret,
                              Environment environment) {
        this.secret = secret;
        this.requireStrongSecret = requireStrongSecret;
        this.environment = environment;
    }

    @PostConstruct
    public void validate() {
        if (secret == null || secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException("JWT secret 至少需要 " + MIN_SECRET_LENGTH + " 个字符");
        }
        if ((requireStrongSecret || isProductionProfile()) && isPlaceholderSecret(secret)) {
            throw new IllegalStateException("生产环境必须配置强随机 JWT_SECRET_KEY");
        }
    }

    private boolean isProductionProfile() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> "prod".equalsIgnoreCase(profile) || "production".equalsIgnoreCase(profile));
    }

    private boolean isPlaceholderSecret(String value) {
        String normalized = value.toLowerCase();
        return DEFAULT_SECRET.equals(value)
                || normalized.contains("change-me")
                || normalized.contains("replace")
                || normalized.contains("your-secret");
    }
}
