package com.example.demo.dao;

import com.example.demo.model.Role;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface RoleRepository extends JpaRepository<Role, Long> {
    List<Role> findByRoleNameIn(Collection<String> roleNames);

    boolean existsByRoleName(String roleName);

    @Override
    @EntityGraph(attributePaths = "rolePermission")
    List<Role> findAll();
}
