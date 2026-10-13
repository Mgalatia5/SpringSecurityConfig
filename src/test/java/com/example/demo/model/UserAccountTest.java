package com.example.demo.model;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class UserAccountTest {

    private static Permission permission(String name, boolean enabled) {
        return Permission.builder().permissionName(name).enabled(enabled).build();
    }

    private static Role role(String name, boolean enabled, Permission... permissions) {
        return Role.builder().roleName(name).enabled(enabled).rolePermission(Set.of(permissions)).build();
    }

    private static Set<String> authorities(UserAccount user) {
        return user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(java.util.stream.Collectors.toSet());
    }

    @Test
    void rolesBecomeRoleAuthoritiesAndPermissionsBecomePlainAuthorities() {
        UserAccount user = UserAccount.builder()
                .userRoles(Set.of(role("USER", true, permission("post:read", true), permission("post:write", true))))
                .build();

        assertThat(authorities(user)).containsExactlyInAnyOrder("ROLE_USER", "post:read", "post:write");
    }

    @Test
    void permissionsFromSeveralRolesAreMerged() {
        UserAccount user = UserAccount.builder()
                .userRoles(Set.of(
                        role("USER", true, permission("post:read", true)),
                        role("MODERATOR", true, permission("post:read", true), permission("post:moderate", true))))
                .build();

        assertThat(authorities(user))
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_MODERATOR", "post:read", "post:moderate");
    }

    @Test
    void disabledRoleGrantsNothing() {
        UserAccount user = UserAccount.builder()
                .userRoles(Set.of(role("ADMIN", false, permission("user:write", true))))
                .build();

        assertThat(authorities(user)).isEmpty();
    }

    @Test
    void disabledPermissionIsSkipped() {
        UserAccount user = UserAccount.builder()
                .userRoles(Set.of(role("USER", true, permission("post:read", true), permission("post:write", false))))
                .build();

        assertThat(authorities(user)).containsExactlyInAnyOrder("ROLE_USER", "post:read");
    }

    @Test
    void builderDefaultsToAnActiveAccount() {
        UserAccount user = UserAccount.builder().username("alice").build();

        assertThat(user.isEnabled()).isTrue();
        assertThat(user.isAccountNonLocked()).isTrue();
        assertThat(user.isAccountNonExpired()).isTrue();
        assertThat(user.isCredentialsNonExpired()).isTrue();
        assertThat(user.getUserRoles()).isEmpty();
        assertThat(user.getLikedPosts()).isEmpty();
    }
}
