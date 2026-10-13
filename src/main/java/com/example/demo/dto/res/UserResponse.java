package com.example.demo.dto.res;

import com.example.demo.model.UserAccount;

public record UserResponse(Long id, String username) {
    public static UserResponse from(UserAccount user) {
        return new UserResponse(user.getId(), user.getUsername());
    }
}
