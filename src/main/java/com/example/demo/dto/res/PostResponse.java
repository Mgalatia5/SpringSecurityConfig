package com.example.demo.dto.res;

import com.example.demo.dto.AuthDto.UserResponse;
import com.example.demo.model.Post;

import java.util.List;

public record PostResponse(Long id, String title, String content, UserResponse userAccount, List<UserResponse> likedByUsers) {
    public static PostResponse from(Post post) {
        return new PostResponse(post.getId(), post.getTitle(), post.getContent(),
                UserResponse.from(post.getUserAccount()),
                post.getLikedByUsers().stream().map(UserResponse::from).toList());
    }
}
