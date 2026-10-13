package com.example.demo.dto.res;

import com.example.demo.model.Role;
import com.example.demo.model.UserAccount;
import org.springframework.security.core.GrantedAuthority;

import java.util.Set;
import java.util.TreeSet;

import static java.util.stream.Collectors.toCollection;

public record UserDetailResponse(Long id, String username, boolean enabled, Set<String> roles, Set<String> permissions) {
    public static UserDetailResponse from(UserAccount user) {
        return new UserDetailResponse(user.getId(), user.getUsername(), user.isEnabled(),
                user.getUserRoles().stream().map(Role::getRoleName).collect(toCollection(TreeSet::new)),
                user.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                        .filter(authority -> !authority.startsWith("ROLE_")).collect(toCollection(TreeSet::new)));
    }
}
