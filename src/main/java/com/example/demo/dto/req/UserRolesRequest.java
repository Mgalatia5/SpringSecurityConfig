package com.example.demo.dto.req;

import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

public record UserRolesRequest(@NotEmpty Set<String> roles) {
}
