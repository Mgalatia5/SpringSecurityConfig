package com.example.demo.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import static com.example.demo.config.properties.PropertyValidation.required;

@ConfigurationProperties("app.admin")
public record AdminProperties(String username, String password) {
    public AdminProperties {
        required("app.admin.username (ADMIN_USERNAME)", username);
        required("app.admin.password (ADMIN_PASSWORD)", password);
    }
}
