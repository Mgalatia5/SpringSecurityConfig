package com.example.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** The {@code app.*} settings from application.yaml, checked at startup. */
@ConfigurationProperties("app")
public record AppProperties(Jwt jwt, Admin admin) {

    /** @param secret HS256 signing key, at least 32 bytes */
    public record Jwt(String secret, Duration accessTokenTtl, Duration refreshTokenTtl) {
        public Jwt {
            if (required("app.jwt.secret (JWT_SECRET)", secret).getBytes(StandardCharsets.UTF_8).length < 32) {
                throw new IllegalStateException("app.jwt.secret (JWT_SECRET) must be at least 32 bytes long for HS256");
            }
        }
    }

    /** The admin account created on first startup. */
    public record Admin(String username, String password) {
        public Admin {
            required("app.admin.username (ADMIN_USERNAME)", username);
            required("app.admin.password (ADMIN_PASSWORD)", password);
        }
    }

    /** An environment variable without a default stays as the literal "${NAME}" text, so treat that as missing. */
    private static String required(String name, String value) {
        if (value == null || value.isBlank() || value.startsWith("${")) {
            throw new IllegalStateException(name + " is not set");
        }
        return value;
    }
}
