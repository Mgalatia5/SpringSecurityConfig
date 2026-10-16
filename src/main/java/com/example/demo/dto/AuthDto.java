package com.example.demo.dto;

import com.example.demo.model.Permission;
import com.example.demo.model.Role;
import com.example.demo.model.UserAccount;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.GrantedAuthority;

import java.util.Set;
import java.util.TreeSet;

import static java.util.stream.Collectors.toCollection;

/** Request and response bodies for authentication, users and roles. */
public interface AuthDto {

    // ---- requests

    /** Username and password, for register, login and admin user creation. BCrypt only uses 72 bytes. */
    record UserRequest(@NotBlank @Size(max = 50) String username, @NotBlank @Size(max = 72) String password) {
    }

    record RefreshRequest(@NotBlank String refreshToken) {
    }

    record UserRolesRequest(@NotEmpty Set<String> roles) {
    }

    /** @param permissions optional, defaults to none */
    record RoleRequest(@NotBlank String roleName, String roleDescription, Set<String> permissions) {
        public RoleRequest {
            permissions = permissions == null ? Set.of() : permissions;
        }
    }

    record RolePermissionsRequest(@NotNull Set<String> permissions) {
    }

    // ---- responses

    record TokenResponse(String accessToken, String tokenType, long expiresIn,
                         String refreshToken, long refreshExpiresIn) {
    }

    record UserResponse(Long id, String username) {
        public static UserResponse from(UserAccount user) {
            return new UserResponse(user.getId(), user.getUsername());
        }
    }

    record UserDetailResponse(Long id, String username, boolean enabled, Set<String> roles, Set<String> permissions) {
        public static UserDetailResponse from(UserAccount user) {
            return new UserDetailResponse(user.getId(), user.getUsername(), user.isEnabled(),
                    user.getUserRoles().stream().map(Role::getRoleName).collect(toCollection(TreeSet::new)),
                    user.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                            .filter(authority -> !authority.startsWith("ROLE_")).collect(toCollection(TreeSet::new)));
        }
    }

    record RoleResponse(Long id, String roleName, String roleDescription, boolean enabled, Set<String> permissions) {
        public static RoleResponse from(Role role) {
            return new RoleResponse(role.getId(), role.getRoleName(), role.getRoleDescription(), role.isEnabled(),
                    role.getRolePermission().stream().map(Permission::getPermissionName).collect(toCollection(TreeSet::new)));
        }
    }

    record PermissionResponse(Long id, String permissionName, String description, boolean enabled) {
        public static PermissionResponse from(Permission permission) {
            return new PermissionResponse(permission.getId(), permission.getPermissionName(),
                    permission.getDescription(), permission.isEnabled());
        }
    }
}
