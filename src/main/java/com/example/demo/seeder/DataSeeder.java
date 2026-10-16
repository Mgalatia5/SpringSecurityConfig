package com.example.demo.seeder;

import com.example.demo.config.properties.AdminProperties;
import com.example.demo.dao.AuthRepositories.PermissionRepository;
import com.example.demo.dao.AuthRepositories.RoleRepository;
import com.example.demo.dao.AuthRepositories.UserRepository;
import com.example.demo.model.Permission;
import com.example.demo.model.Role;
import com.example.demo.model.UserAccount;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

/**
 * Runs once at startup: creates the default permissions, roles and admin account if they don't exist yet.
 * Existing rows are never changed, so edits made through the API survive restarts.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {
    private static final Map<String, String> PERMISSIONS = Map.of(
            "post:read", "View posts",
            "post:write", "Create posts and delete own posts",
            "post:like", "Like and unlike posts",
            "post:moderate", "Delete any post",
            "user:read", "View all users",
            "user:write", "Create users and assign roles",
            "role:read", "View roles and permissions",
            "role:write", "Create roles and change their permissions");

    private record SeedRole(String name, String description, Set<String> permissions) {
    }

    private static final List<SeedRole> ROLES = List.of(
            new SeedRole(Role.DEFAULT_ROLE, "Regular user", Set.of("post:read", "post:write", "post:like")),
            new SeedRole("MODERATOR", "Can moderate posts",
                    Set.of("post:read", "post:write", "post:like", "post:moderate", "user:read")),
            new SeedRole("ADMIN", "Full access", PERMISSIONS.keySet()));

    private final UserRepository users;
    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final PasswordEncoder passwordEncoder;
    private final AdminProperties admin;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Map<String, Permission> existing = permissions.findAll().stream()
                .collect(toMap(Permission::getPermissionName, Function.identity()));
        PERMISSIONS.forEach((name, description) -> existing.computeIfAbsent(name, missing -> permissions.save(
                Permission.builder().permissionName(name).description(description).build())));

        ROLES.stream().filter(seed -> !roles.existsByRoleName(seed.name())).forEach(seed -> {
            roles.save(Role.builder().roleName(seed.name()).roleDescription(seed.description())
                    .rolePermission(seed.permissions().stream().map(existing::get).collect(toSet())).build());
            log.info("Created role {}", seed.name());
        });

        String username = admin.username();
        if (!users.existsByUsername(username)) {
            users.save(UserAccount.builder()
                    .username(username)
                    .password(passwordEncoder.encode(admin.password()))
                    .userRoles(new HashSet<>(roles.findByRoleNameIn(Set.of("ADMIN"))))
                    .build());
            log.info("Created admin user '{}'", username);
        }
    }
}
