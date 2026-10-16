package com.example.demo.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Entity
@Table(name = "user_account")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAccount implements UserDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    private String password;

    @Builder.Default
    @Column(name = "is_enabled")
    private boolean enabled = true;

    @ManyToMany
    @JoinTable(name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"), inverseJoinColumns = @JoinColumn(name = "role_id"))
    @Builder.Default
    private Set<Role> userRoles = new HashSet<>();

    @ManyToMany
    @JoinTable(name = "post_likes",
            joinColumns = @JoinColumn(name = "user_id"), inverseJoinColumns = @JoinColumn(name = "post_id"))
    @Builder.Default
    private Set<Post> likedPosts = new HashSet<>();

    @Override
    public Set<GrantedAuthority> getAuthorities() {
        return userRoles.stream()
                .filter(Role::isEnabled)
                .flatMap(role -> Stream.concat(
                        Stream.of("ROLE_" + role.getRoleName()),
                        role.getRolePermission().stream().filter(Permission::isEnabled).map(Permission::getPermissionName)))
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());
    }

    public boolean hasAuthority(String authority) {
        return getAuthorities().contains(new SimpleGrantedAuthority(authority));
    }
}
