package com.example.demo.service;

import com.example.demo.dao.AuthRepositories.UserRepository;
import com.example.demo.dto.AuthDto.UserDetailResponse;
import com.example.demo.dto.AuthDto.UserRequest;
import com.example.demo.dto.AuthDto.UserResponse;
import com.example.demo.exception.ApiException;
import com.example.demo.model.Role;
import com.example.demo.model.UserAccount;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

import static com.example.demo.exception.ApiException.notFound;
import static org.springframework.http.HttpStatus.CONFLICT;

/**
 * User accounts and their roles.
 */
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository users;
    private final RoleService roleService;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<UserDetailResponse> getUsers() {
        return users.findAll().stream().map(UserDetailResponse::from).toList();
    }

    /**
     * New users get the default role only.
     */
    @Transactional
    public UserResponse createUser(UserRequest request) {
        if (users.existsByUsername(request.username())) {
            throw new ApiException(CONFLICT, "Username '%s' is already taken".formatted(request.username()));
        }
        return UserResponse.from(users.save(UserAccount.builder()
                .username(request.username())
                .password(passwordEncoder.encode(request.password()))
                .userRoles(roleService.findRoles(Set.of(Role.DEFAULT_ROLE)))
                .build()));
    }

    @Transactional
    public UserDetailResponse updateRoles(Long userId, Set<String> roleNames) {
        UserAccount user = users.findById(userId).orElseThrow(notFound("User", userId));
        user.setUserRoles(roleService.findRoles(roleNames));
        return UserDetailResponse.from(user);
    }
}
