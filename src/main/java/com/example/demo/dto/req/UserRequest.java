package com.example.demo.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Username and password, for register, login and admin user creation. BCrypt only uses 72 bytes. */
public record UserRequest(@NotBlank @Size(max = 50) String username, @NotBlank @Size(max = 72) String password) {
}
