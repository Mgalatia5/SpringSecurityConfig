package com.example.demo.dto.req;

import jakarta.validation.constraints.NotNull;

import java.util.Set;

public record RolePermissionsRequest(@NotNull Set<String> permissions) {
}
