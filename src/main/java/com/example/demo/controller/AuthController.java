package com.example.demo.controller;

import com.example.demo.config.security.UserJwtAuthenticationToken;
import com.example.demo.dto.AuthDto.*;
import com.example.demo.model.UserAccount;
import com.example.demo.service.SessionService;
import com.example.demo.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import static org.springframework.http.HttpStatus.NO_CONTENT;

/** Registration, login and sessions. */
@RestController
@RequestMapping("api/vi/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final SessionService sessionService;
    private final UserService userService;

    @PostMapping("/register")
    public UserResponse register(@Valid @RequestBody UserRequest request) {
        return userService.createUser(request);
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody UserRequest request) {
        var authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));
        return sessionService.login((UserAccount) authentication.getPrincipal());
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return sessionService.refresh(request.refreshToken());
    }

    @GetMapping("/me")
    public UserDetailResponse me(@AuthenticationPrincipal UserAccount user) {
        return UserDetailResponse.from(user);
    }

    @PostMapping("/logout")
    @ResponseStatus(NO_CONTENT)
    public void logout(UserJwtAuthenticationToken authentication) {
        sessionService.logout(authentication.getCredentials());
    }

    @PostMapping("/logout-all")
    @ResponseStatus(NO_CONTENT)
    public void logoutAll(@AuthenticationPrincipal UserAccount user) {
        sessionService.logoutAll(user.getId());
    }
}
