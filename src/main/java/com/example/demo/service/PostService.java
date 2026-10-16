package com.example.demo.service;

import com.example.demo.dao.PostRepository;
import com.example.demo.dao.AuthRepositories.UserRepository;
import com.example.demo.dto.req.PostRequest;
import com.example.demo.dto.res.PostResponse;
import com.example.demo.model.Post;
import com.example.demo.model.UserAccount;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.example.demo.exception.ApiException.notFound;

@Service
@RequiredArgsConstructor
public class PostService {
    private final PostRepository posts;
    private final UserRepository users;

    @Transactional(readOnly = true)
    public List<PostResponse> getPosts() {
        return posts.findAll().stream().map(PostResponse::from).toList();
    }

    @Transactional
    public PostResponse createPost(PostRequest request, Long authorId) {
        return PostResponse.from(posts.save(Post.builder()
                .title(request.title())
                .content(request.content())
                .userAccount(users.getReferenceById(authorId))
                .build()));
    }

    /** Authors can delete their own posts; anyone with post:moderate can delete any post. */
    @Transactional
    public void deletePost(Long postId, UserAccount currentUser) {
        Post post = posts.findById(postId).orElseThrow(notFound("Post", postId));
        if (!post.getUserAccount().getId().equals(currentUser.getId()) && !currentUser.hasAuthority("post:moderate")) {
            throw new AccessDeniedException("Access denied: you can only delete your own posts unless you have post:moderate");
        }
        post.getLikedByUsers().forEach(user -> user.getLikedPosts().remove(post));   // likes are owned by UserAccount
        posts.delete(post);
    }

    /**
     * Likes or unlikes the post. Doing it twice has no extra effect.
     */
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
