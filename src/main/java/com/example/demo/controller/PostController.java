package com.example.demo.controller;

import com.example.demo.dto.req.PostRequest;
import com.example.demo.dto.res.PostResponse;
import com.example.demo.model.UserAccount;
import com.example.demo.service.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static org.springframework.http.HttpStatus.NO_CONTENT;

@RestController
@RequestMapping("api/vi/post")
@RequiredArgsConstructor
public class PostController {
    private final PostService postService;

    @GetMapping
    @PreAuthorize("hasAuthority('post:read')")
    public List<PostResponse> getPosts() {
        return postService.getPosts();
    }

    /** The author is always the logged-in user. */
    @PostMapping
    @PreAuthorize("hasAuthority('post:write')")
    public PostResponse createPost(@Valid @RequestBody PostRequest request, @AuthenticationPrincipal UserAccount user) {
        return postService.createPost(request, user.getId());
    }

    @DeleteMapping("/{postId}")
    @PreAuthorize("hasAnyAuthority('post:write', 'post:moderate')")
    @ResponseStatus(NO_CONTENT)
    public void deletePost(@PathVariable Long postId, @AuthenticationPrincipal UserAccount user) {
        postService.deletePost(postId, user);
    }
}
