package com.example.demo.dao;

import com.example.demo.model.Post;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {
    /** Authors and likes in the same query, instead of two extra queries per post. */
    @Override
    @EntityGraph(attributePaths = {"userAccount", "likedByUsers"})
    List<Post> findAll();
}
