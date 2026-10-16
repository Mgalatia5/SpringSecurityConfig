package com.example.demo.controller;

import com.example.demo.IntegrationTestSupport;
import com.example.demo.dao.AuthRepositories.UserRepository;
import com.example.demo.model.UserAccount;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest extends IntegrationTestSupport {

    @Autowired
    private UserRepository userRepository;

    @Test
    void registerReturnsTheUserWithoutPassword() throws Exception {
        String username = uniqueName("alice");

        mockMvc.perform(post("/api/vi/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(username, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void passwordIsStoredAsBcryptHash() throws Exception {
        String username = uniqueName("alice");
        register(username);

        String stored = userRepository.findByUsername(username).orElseThrow().getPassword();
        assertThat(stored).startsWith("$2").isNotEqualTo(PASSWORD);
    }

    @Test
    void registerRejectsDuplicateUsername() throws Exception {
        String username = uniqueName("alice");
        register(username);

        mockMvc.perform(post("/api/vi/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(username, "other")))
                .andExpect(status().isConflict());
    }

    @Test
    void registerRejectsBlankCredentials() throws Exception {
        mockMvc.perform(post("/api/vi/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("", "")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginReturnsBearerToken() throws Exception {
        String username = uniqueName("alice");
        register(username);

        mockMvc.perform(post("/api/vi/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(username, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    void loginRejectsWrongPassword() throws Exception {
        String username = uniqueName("alice");
        register(username);

        mockMvc.perform(post("/api/vi/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(username, "wrong")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginRejectsUnknownUser() throws Exception {
        mockMvc.perform(post("/api/vi/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(uniqueName("nobody"), PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meShowsDefaultRoleAndPermissions() throws Exception {
        TestUser alice = newUser("alice");

        mockMvc.perform(get("/api/vi/auth/me").with(bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(alice.id()))
                .andExpect(jsonPath("$.username").value(alice.username()))
                .andExpect(jsonPath("$.roles", contains("USER")))
                .andExpect(jsonPath("$.permissions", containsInAnyOrder("post:read", "post:write", "post:like")));
    }

    @Test
    void requestWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/vi/post"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requestWithInvalidTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/vi/post").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void disabledUserIsLockedOutImmediately() throws Exception {
        TestUser alice = newUser("alice");
        UserAccount account = userRepository.findByUsername(alice.username()).orElseThrow();
        account.setEnabled(false);
        userRepository.save(account);

        // the token issued before the account was disabled no longer works
        mockMvc.perform(get("/api/vi/auth/me").with(bearer(alice)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/vi/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(alice.username(), PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesTheToken() throws Exception {
        TestUser alice = newUser("alice");

        mockMvc.perform(post("/api/vi/auth/logout").with(bearer(alice)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/vi/auth/me").with(bearer(alice)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/vi/auth/logout").with(bearer(alice)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutOnlyEndsTheCurrentSession() throws Exception {
        TestUser laptop = newUser("alice");
        TestUser phone = new TestUser(laptop.id(), laptop.username(), login(laptop.username(), PASSWORD));

        mockMvc.perform(post("/api/vi/auth/logout").with(bearer(laptop)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/vi/auth/me").with(bearer(phone)))
                .andExpect(status().isOk());
    }

    @Test
    void userCanLogInAgainAfterLogout() throws Exception {
        TestUser alice = newUser("alice");
        mockMvc.perform(post("/api/vi/auth/logout").with(bearer(alice)))
                .andExpect(status().isNoContent());

        TestUser again = new TestUser(alice.id(), alice.username(), login(alice.username(), PASSWORD));
        mockMvc.perform(get("/api/vi/auth/me").with(bearer(again)))
                .andExpect(status().isOk());
    }

    @Test
    void logoutWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/vi/auth/logout"))
                .andExpect(status().isUnauthorized());
    }
}
