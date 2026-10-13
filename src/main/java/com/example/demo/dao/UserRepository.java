package com.example.demo.dao;

import com.example.demo.model.UserAccount;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<UserAccount, Long> {
    /** Loads roles and permissions in the same query, as they're needed for every request. */
    @EntityGraph(attributePaths = {"userRoles", "userRoles.rolePermission"})
    Optional<UserAccount> findByUsername(String username);

    boolean existsByUsername(String username);

    @Override
    @EntityGraph(attributePaths = {"userRoles", "userRoles.rolePermission"})
    List<UserAccount> findAll();
}
