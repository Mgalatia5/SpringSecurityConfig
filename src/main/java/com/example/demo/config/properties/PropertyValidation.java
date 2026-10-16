package com.example.demo.config.properties;

final class PropertyValidation {
    private PropertyValidation() {
    }

    static String required(String name, String value) {
        if (value == null || value.isBlank() || value.startsWith("${")) {
            throw new IllegalStateException(name + " is not set");
        }
        return value;
    }
}
