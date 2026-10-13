package com.example.demo.controller;

import com.example.demo.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Admin-only endpoints (users, roles, permissions) and how role changes take effect. */
class AuthorizationTest extends IntegrationTestSupport {

    // Bodies are valid so the request reaches the permission check instead of failing validation.
    @ParameterizedTest(name = "{0} {1}")
    @CsvSource(delimiter = '|', value = {
            "GET    | /api/vi/user                   | ",
            "POST   | /api/vi/user                   | {\"username\":\"x\",\"password\":\"y\"}",
            "PUT    | /api/vi/user/1/roles           | {\"roles\":[\"ADMIN\"]}",
            "GET    | /api/vi/role                   | ",
            "GET    | /api/vi/role/permissions       | ",
            "POST   | /api/vi/role                   | {\"roleName\":\"HACKER\",\"permissions\":[\"role:write\"]}",
            "PUT    | /api/vi/role/1/permissions     | {\"permissions\":[\"role:write\"]}"
    })
    void regularUserCannotUseAdminEndpoints(String method, String path, String json) throws Exception {
        TestUser alice = newUser("alice");
        var request = request(HttpMethod.valueOf(method), path).with(bearer(alice));
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }

        mockMvc.perform(request).andExpect(status().isForbidden());
    }

    @Test
    void seededRolesHaveTheExpectedPermissions() throws Exception {
        mockMvc.perform(get("/api/vi/role").with(bearer(admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.roleName == 'USER')].permissions[*]",
                        containsInAnyOrder("post:read", "post:write", "post:like")))
                .andExpect(jsonPath("$[?(@.roleName == 'MODERATOR')].permissions[*]",
                        containsInAnyOrder("post:read", "post:write", "post:like", "post:moderate", "user:read")))
                .andExpect(jsonPath("$[?(@.roleName == 'ADMIN')].permissions[*]", hasSize(8)));
    }

    @Test
    void adminCanListPermissions() throws Exception {
        mockMvc.perform(get("/api/vi/role/permissions").with(bearer(admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].permissionName", hasItems(
                        "post:read", "post:write", "post:like", "post:moderate",
                        "user:read", "user:write", "role:read", "role:write")));
    }

    @Test
    void adminCanListUsersWithTheirRoles() throws Exception {
        TestUser alice = newUser("alice");

        mockMvc.perform(get("/api/vi/user").with(bearer(admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.username == '%s')].roles[*]".formatted(alice.username()), contains("USER")))
                .andExpect(jsonPath("$[*].password").doesNotExist());
    }

    @Test
    void roleChangeAppliesToAnExistingToken() throws Exception {
        TestUser bob = newUser("bob");
        mockMvc.perform(get("/api/vi/user").with(bearer(bob)))
                .andExpect(status().isForbidden());

        assignRoles(bob, "MODERATOR");

        // same token, new permissions: authorities are loaded from the database on every request
        mockMvc.perform(get("/api/vi/user").with(bearer(bob)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/vi/auth/me").with(bearer(bob)))
                .andExpect(jsonPath("$.roles", contains("MODERATOR")))
                .andExpect(jsonPath("$.permissions", hasItem("post:moderate")));
    }

    @Test
    void moderatorCannotManageRoles() throws Exception {
        TestUser moderator = newUser("mod");
        assignRoles(moderator, "MODERATOR");

        mockMvc.perform(post("/api/vi/role")
                        .with(bearer(moderator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleName\":\"%s\"}".formatted(uniqueName("ROLE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void assigningUnknownRoleIsBadRequest() throws Exception {
        TestUser alice = newUser("alice");

        mockMvc.perform(put("/api/vi/user/{id}/roles", alice.id())
                        .with(bearer(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"NOPE\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void assigningRolesToUnknownUserIsNotFound() throws Exception {
        mockMvc.perform(put("/api/vi/user/{id}/roles", 999_999_999L)
                        .with(bearer(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"USER\"]}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void customRoleControlsAccessAndCanBeChanged() throws Exception {
        TestUser admin = admin();
        String roleName = uniqueName("reader");

        String created = body(mockMvc.perform(post("/api/vi/role")
                        .with(bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleName\":\"%s\",\"roleDescription\":\"Read-only\",\"permissions\":[\"post:read\"]}"
                                .formatted(roleName)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roleName").value(roleName.toUpperCase()))
                .andExpect(jsonPath("$.permissions", contains("post:read"))));
        long roleId = com.jayway.jsonpath.JsonPath.<Number>read(created, "$.id").longValue();

        TestUser alice = newUser("alice");
        assignRoles(alice, roleName.toUpperCase());
        mockMvc.perform(get("/api/vi/post").with(bearer(alice)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/vi/post")
                        .with(bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"content\":\"c\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/vi/role/{id}/permissions", roleId)
                        .with(bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissions\":[\"post:read\",\"post:write\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions", containsInAnyOrder("post:read", "post:write")));
        createPost(alice, "Now allowed");
    }

    @Test
    void creatingDuplicateRoleIsConflict() throws Exception {
        mockMvc.perform(post("/api/vi/role")
                        .with(bearer(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleName\":\"admin\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void creatingRoleWithUnknownPermissionIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/vi/role")
                        .with(bearer(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleName\":\"%s\",\"permissions\":[\"no:such\"]}".formatted(uniqueName("role"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updatingUnknownRoleIsNotFound() throws Exception {
        mockMvc.perform(put("/api/vi/role/{id}/permissions", 999_999_999L)
                        .with(bearer(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissions\":[\"post:read\"]}"))
                .andExpect(status().isNotFound());
    }
}
