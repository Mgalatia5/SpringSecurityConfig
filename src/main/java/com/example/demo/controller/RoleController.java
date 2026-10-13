package com.example.demo.controller;

import com.example.demo.dto.req.RolePermissionsRequest;
import com.example.demo.dto.req.RoleRequest;
import com.example.demo.dto.res.PermissionResponse;
import com.example.demo.dto.res.RoleResponse;
import com.example.demo.service.RoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/vi/role")
@RequiredArgsConstructor
public class RoleController {
    private final RoleService roleService;

    @GetMapping
    @PreAuthorize("hasAuthority('role:read')")
    public List<RoleResponse> getRoles() {
        return roleService.getRoles();
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('role:read')")
    public List<PermissionResponse> getPermissions() {
        return roleService.getPermissions();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('role:write')")
    public RoleResponse createRole(@Valid @RequestBody RoleRequest request) {
        return roleService.createRole(request);
    }

    @PutMapping("/{roleId}/permissions")
    @PreAuthorize("hasAuthority('role:write')")
    public RoleResponse updatePermissions(@PathVariable Long roleId, @Valid @RequestBody RolePermissionsRequest request) {
        return roleService.updatePermissions(roleId, request.permissions());
    }
}
