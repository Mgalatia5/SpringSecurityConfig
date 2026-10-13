package com.example.demo.dao;

import com.example.demo.model.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PermissionRepository extends JpaRepository<Permission, Long> {
    List<Permission> findByPermissionNameIn(Collection<String> permissionNames);
}
