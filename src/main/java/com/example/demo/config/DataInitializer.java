package com.example.demo.config;

import com.example.demo.dao.PermissionRepository;
import com.example.demo.dao.RoleRepository;
import com.example.demo.dao.UserRepository;
import com.example.demo.model.Permission;
import com.example.demo.model.Role;
import com.example.demo.model.UserAccount;
import com.example.demo.service.RoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

/**
 * Creates the default permissions, roles and admin account if they don't exist yet.
 * Existing rows are never changed, so edits made through the API survive restarts.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {
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

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final RoleService roleService;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties properties;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Map<String, Permission> permissions = permissionRepository.findAll().stream()
                .collect(toMap(Permission::getPermissionName, Function.identity()));
        PERMISSIONS.forEach((name, description) -> permissions.computeIfAbsent(name, missing -> permissionRepository.save(
                Permission.builder().permissionName(name).description(description).build())));

        ROLES.stream().filter(seed -> !roleRepository.existsByRoleName(seed.name())).forEach(seed -> {
            roleRepository.save(Role.builder().roleName(seed.name()).roleDescription(seed.description())
                    .rolePermission(seed.permissions().stream().map(permissions::get).collect(toSet())).build());
            log.info("Created role {}", seed.name());
        });

        String admin = properties.admin().username();
        if (!userRepository.existsByUsername(admin)) {
            userRepository.save(UserAccount.builder()
                    .username(admin)
                    .password(passwordEncoder.encode(properties.admin().password()))
                    .userRoles(roleService.findRoles(Set.of("ADMIN")))
                    .build());
            log.info("Created admin user '{}'", admin);
        }
    }
}
