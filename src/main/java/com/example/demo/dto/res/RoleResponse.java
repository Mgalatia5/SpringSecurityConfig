package com.example.demo.dto.res;

import com.example.demo.model.Permission;
import com.example.demo.model.Role;

import java.util.Set;
import java.util.TreeSet;

import static java.util.stream.Collectors.toCollection;

public record RoleResponse(Long id, String roleName, String roleDescription, boolean enabled, Set<String> permissions) {
    public static RoleResponse from(Role role) {
        return new RoleResponse(role.getId(), role.getRoleName(), role.getRoleDescription(), role.isEnabled(),
                role.getRolePermission().stream().map(Permission::getPermissionName).collect(toCollection(TreeSet::new)));
    }
}
