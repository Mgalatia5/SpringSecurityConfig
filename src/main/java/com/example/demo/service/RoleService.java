package com.example.demo.service;

import com.example.demo.dao.AuthRepositories.PermissionRepository;
import com.example.demo.dao.AuthRepositories.RoleRepository;
import com.example.demo.dto.AuthDto.PermissionResponse;
import com.example.demo.dto.AuthDto.RoleRequest;
import com.example.demo.dto.AuthDto.RoleResponse;
import com.example.demo.exception.ApiException;
import com.example.demo.model.Permission;
import com.example.demo.model.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.example.demo.exception.ApiException.notFound;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;

/**
 * Roles and the permissions they grant.
 */
@Service
@RequiredArgsConstructor
public class RoleService {
    private final RoleRepository roles;
    private final PermissionRepository permissions;

    @Transactional(readOnly = true)
    public List<RoleResponse> getRoles() {
        return roles.findAll().stream().map(RoleResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> getPermissions() {
        return permissions.findAll().stream().map(PermissionResponse::from).toList();
    }

    @Transactional
    public RoleResponse createRole(RoleRequest request) {
        String name = request.roleName().trim().toUpperCase();
        if (roles.existsByRoleName(name)) {
            throw new ApiException(CONFLICT, "Role '%s' already exists".formatted(name));
        }
        return RoleResponse.from(roles.save(Role.builder().roleName(name).roleDescription(request.roleDescription())
                .rolePermission(findPermissions(request.permissions())).build()));
    }

    @Transactional
    public RoleResponse updatePermissions(Long roleId, Set<String> permissionNames) {
        Role role = roles.findById(roleId).orElseThrow(notFound("Role", roleId));
        role.setRolePermission(findPermissions(permissionNames));
        return RoleResponse.from(role);
    }

    /**
     * The roles with these names; 400 if any name is unknown.
     */
    @Transactional(readOnly = true)
    public Set<Role> findRoles(Set<String> names) {
        return requireAll("roles", names, roles.findByRoleNameIn(names), Role::getRoleName, roles::findAll);
    }

    private Set<Permission> findPermissions(Set<String> names) {
        return requireAll("permissions", names, permissions.findByPermissionNameIn(names),
                Permission::getPermissionName, permissions::findAll);
    }

    /**
     * Returns {@code found} if it covers every name, else a 400 listing the unknown and the available names.
     */
    private static <T> Set<T> requireAll(String kind, Set<String> names, List<T> found,
                                         Function<T, String> nameOf, Supplier<List<T>> all) {
        Set<String> unknown = new TreeSet<>(names);
        found.stream().map(nameOf).forEach(unknown::remove);
        if (!unknown.isEmpty()) {
            List<String> available = all.get().stream().map(nameOf).sorted().toList();
            throw new ApiException(BAD_REQUEST, "Unknown %s: %s. Available %s: %s".formatted(kind, unknown, kind, available));
        }
        return new HashSet<>(found);
    }
}
