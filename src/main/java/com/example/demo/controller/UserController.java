package com.example.demo.controller;

import com.example.demo.dto.req.UserRequest;
import com.example.demo.dto.req.UserRolesRequest;
import com.example.demo.dto.res.UserDetailResponse;
import com.example.demo.dto.res.UserResponse;
import com.example.demo.model.UserAccount;
import com.example.demo.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static org.springframework.http.HttpStatus.NO_CONTENT;

@RestController
@RequestMapping("api/vi/user")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasAuthority('user:read')")
    public List<UserDetailResponse> getUsers() {
        return userService.getUsers();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('user:write')")
    public UserResponse createUser(@Valid @RequestBody UserRequest request) {
        return userService.createUser(request);
    }

    @PutMapping("/{userId}/roles")
    @PreAuthorize("hasAuthority('user:write')")
    public UserDetailResponse updateRoles(@PathVariable Long userId, @Valid @RequestBody UserRolesRequest request) {
        return userService.updateRoles(userId, request.roles());
    }

    @PostMapping("/{userId}/likes/{postId}")
    @PreAuthorize("hasAuthority('post:like')")
    @ResponseStatus(NO_CONTENT)
    public void likePost(@PathVariable Long userId, @PathVariable Long postId, @AuthenticationPrincipal UserAccount user) {
        userService.setLiked(self(userId, user), postId, true);
    }

    @DeleteMapping("/{userId}/likes/{postId}")
    @PreAuthorize("hasAuthority('post:like')")
    @ResponseStatus(NO_CONTENT)
    public void unlikePost(@PathVariable Long userId, @PathVariable Long postId, @AuthenticationPrincipal UserAccount user) {
        userService.setLiked(self(userId, user), postId, false);
    }

    /** Likes can only be changed for yourself. */
    private static Long self(Long userId, UserAccount user) {
        if (!userId.equals(user.getId())) {
            throw new AccessDeniedException(
                    "Access denied: you can only like or unlike posts as yourself (your user id is %d)".formatted(user.getId()));
        }
        return userId;
    }
}
