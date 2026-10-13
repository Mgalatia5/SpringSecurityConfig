package com.example.demo.controller;

import com.example.demo.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PostControllerTest extends IntegrationTestSupport {

    @Test
    void authorIsAlwaysTheLoggedInUser() throws Exception {
        TestUser alice = newUser("alice");
        TestUser bob = newUser("bob");

        // a userId in the body is ignored, so alice cannot post as bob
        mockMvc.perform(post("/api/vi/post")
                        .with(bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Hello\",\"content\":\"First\",\"userId\":%d}".formatted(bob.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Hello"))
                .andExpect(jsonPath("$.userAccount.id").value(alice.id()))
                .andExpect(jsonPath("$.userAccount.username").value(alice.username()))
                .andExpect(jsonPath("$.userAccount.password").doesNotExist());
    }

    @Test
    void listPostsShowsCreatedPost() throws Exception {
        TestUser alice = newUser("alice");
        long postId = createPost(alice, "Listed");

        mockMvc.perform(get("/api/vi/post").with(bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == %d)].title".formatted(postId)).value("Listed"));
    }

    @Test
    void authorCanDeleteOwnPost() throws Exception {
        TestUser alice = newUser("alice");
        long postId = createPost(alice, "Mine");

        mockMvc.perform(delete("/api/vi/post/{id}", postId).with(bearer(alice)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/vi/post/{id}", postId).with(bearer(alice)))
                .andExpect(status().isNotFound());
    }

    @Test
    void userCannotDeleteSomeoneElsesPost() throws Exception {
        TestUser alice = newUser("alice");
        TestUser bob = newUser("bob");
        long postId = createPost(alice, "Alice's");

        mockMvc.perform(delete("/api/vi/post/{id}", postId).with(bearer(bob)))
                .andExpect(status().isForbidden());
    }

    @Test
    void moderatorCanDeleteAnyPostIncludingLikedOnes() throws Exception {
        TestUser alice = newUser("alice");
        TestUser moderator = newUser("mod");
        assignRoles(moderator, "MODERATOR");
        long postId = createPost(alice, "Liked");
        mockMvc.perform(post("/api/vi/user/{u}/likes/{p}", alice.id(), postId).with(bearer(alice)))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/vi/post/{id}", postId).with(bearer(moderator)))
                .andExpect(status().isNoContent());
        assertThat(likerIds(alice, postId)).isEmpty();
    }

    @Test
    void likeIsIdempotentAndUnlikeRemovesIt() throws Exception {
        TestUser alice = newUser("alice");
        long postId = createPost(alice, "Like me");

        mockMvc.perform(post("/api/vi/user/{u}/likes/{p}", alice.id(), postId).with(bearer(alice)))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/vi/user/{u}/likes/{p}", alice.id(), postId).with(bearer(alice)))
                .andExpect(status().isNoContent());
        assertThat(likerIds(alice, postId)).containsExactly(alice.id());

        mockMvc.perform(delete("/api/vi/user/{u}/likes/{p}", alice.id(), postId).with(bearer(alice)))
                .andExpect(status().isNoContent());
        assertThat(likerIds(alice, postId)).isEmpty();

        // unliking again is a no-op
        mockMvc.perform(delete("/api/vi/user/{u}/likes/{p}", alice.id(), postId).with(bearer(alice)))
                .andExpect(status().isNoContent());
    }

    @Test
    void userCannotLikeOrUnlikeAsSomeoneElse() throws Exception {
        TestUser alice = newUser("alice");
        TestUser bob = newUser("bob");
        long postId = createPost(alice, "Not yours");

        mockMvc.perform(post("/api/vi/user/{u}/likes/{p}", bob.id(), postId).with(bearer(alice)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/vi/user/{u}/likes/{p}", bob.id(), postId).with(bearer(alice)))
                .andExpect(status().isForbidden());
        assertThat(likerIds(alice, postId)).isEmpty();
    }

    @Test
    void likingUnknownPostIsNotFound() throws Exception {
        TestUser alice = newUser("alice");

        mockMvc.perform(post("/api/vi/user/{u}/likes/{p}", alice.id(), 999_999_999L).with(bearer(alice)))
                .andExpect(status().isNotFound());
    }
}
