package com.example.demo.dao;

import com.example.demo.model.Permission;
import com.example.demo.model.RefreshToken;
import com.example.demo.model.Role;
import com.example.demo.model.UserAccount;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Repositories for users, roles, permissions and refresh tokens (nested repositories are enabled in DemoApplication). */
public interface AuthRepositories {

    interface UserRepository extends JpaRepository<UserAccount, Long> {
        /** Loads roles and permissions in the same query, as they're needed for every request. */
        @EntityGraph(attributePaths = {"userRoles", "userRoles.rolePermission"})
        Optional<UserAccount> findByUsername(String username);

        boolean existsByUsername(String username);

        @Override
        @EntityGraph(attributePaths = {"userRoles", "userRoles.rolePermission"})
        List<UserAccount> findAll();
    }

    interface RoleRepository extends JpaRepository<Role, Long> {
        List<Role> findByRoleNameIn(Collection<String> roleNames);

        boolean existsByRoleName(String roleName);

        @Override
        @EntityGraph(attributePaths = "rolePermission")
        List<Role> findAll();
    }

    interface PermissionRepository extends JpaRepository<Permission, Long> {
        List<Permission> findByPermissionNameIn(Collection<String> permissionNames);
    }

    interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
        Optional<RefreshToken> findByTokenHash(String tokenHash);

        boolean existsBySessionIdAndRevokedTrue(String sessionId);

        @Modifying
        @Query("update RefreshToken t set t.revoked = true where t.sessionId = :sessionId")
        void revokeSession(String sessionId);

        @Modifying
        @Query("update RefreshToken t set t.revoked = true where t.user.id = :userId and t.revoked = false")
        void revokeAllForUser(Long userId);

        @Modifying
        @Query("delete from RefreshToken t where t.expiresAt < :now")
        void deleteExpired(Instant now);
    }
}
