package com.example.demo.service;

import com.example.demo.dao.PostRepository;
import com.example.demo.dao.UserRepository;
import com.example.demo.dto.req.UserRequest;
import com.example.demo.dto.res.UserDetailResponse;
import com.example.demo.dto.res.UserResponse;
import com.example.demo.exception.ApiException;
import com.example.demo.model.Post;
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

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository users;
    private final PostRepository posts;
    private final RoleService roleService;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<UserDetailResponse> getUsers() {
        return users.findAll().stream().map(UserDetailResponse::from).toList();
    }

    /** New users get the default role only. */
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

    /** Likes or unlikes the post. Doing it twice has no extra effect. */
    @Transactional
    public void setLiked(Long userId, Long postId, boolean liked) {
        UserAccount user = users.findById(userId).orElseThrow(notFound("User", userId));
        Post post = posts.findById(postId).orElseThrow(notFound("Post", postId));
        if (liked) {
            user.getLikedPosts().add(post);
        } else {
            user.getLikedPosts().remove(post);
        }
    }
}
