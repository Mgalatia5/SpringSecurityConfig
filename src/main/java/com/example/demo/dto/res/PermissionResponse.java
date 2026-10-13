package com.example.demo.dto.res;

import com.example.demo.model.Permission;

public record PermissionResponse(Long id, String permissionName, String description, boolean enabled) {
    public static PermissionResponse from(Permission permission) {
        return new PermissionResponse(permission.getId(), permission.getPermissionName(),
                permission.getDescription(), permission.isEnabled());
    }
}
