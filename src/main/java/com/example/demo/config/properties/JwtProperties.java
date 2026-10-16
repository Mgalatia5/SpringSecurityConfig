package com.example.demo.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

import static com.example.demo.config.properties.PropertyValidation.required;
import static java.nio.charset.StandardCharsets.UTF_8;

@ConfigurationProperties("app.jwt")
public record JwtProperties(String secret, Duration accessTokenTtl, Duration refreshTokenTtl) {
    public JwtProperties {
        if (required("app.jwt.secret (JWT_SECRET)", secret).getBytes(UTF_8).length < 32) {
            throw new IllegalStateException("app.jwt.secret (JWT_SECRET) must be at least 32 bytes long for HS256");
        }
    }
}
