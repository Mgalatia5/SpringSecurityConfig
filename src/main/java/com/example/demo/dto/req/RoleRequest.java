package com.example.demo.dto.req;

import jakarta.validation.constraints.NotBlank;

import java.util.Set;

/** @param permissions optional, defaults to none */
public record RoleRequest(@NotBlank String roleName, String roleDescription, Set<String> permissions) {
    public RoleRequest {
        permissions = permissions == null ? Set.of() : permissions;
    }
}
