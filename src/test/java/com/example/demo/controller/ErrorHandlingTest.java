package com.example.demo.controller;

import com.example.demo.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Every error has the same JSON shape and a message that explains the cause. */
class ErrorHandlingTest extends IntegrationTestSupport {

    /** Asserts the common error body: status, reason phrase, message, path, timestamp and no stack trace. */
    private static void assertError(ResultActions result, int status, String error, String message, String path)
            throws Exception {
        result.andExpect(status().is(status))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.error").value(error))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.path").value(path))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    // --- 401 --------------------------------------------------------------------------------

    @Test
    void missingTokenExplainsHowToAuthenticate() throws Exception {
        ResultActions result = mockMvc.perform(get("/api/vi/post"));

        assertError(result, 401, "Unauthorized",
                "Authentication required: send a token in the 'Authorization: Bearer <token>' header",
                "/api/vi/post");
        result.andExpect(header().string("WWW-Authenticate", "Bearer"));
    }

    @Test
    void malformedTokenIsReportedAsInvalid() throws Exception {
        mockMvc.perform(get("/api/vi/post").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", startsWith("Invalid token: ")));
    }

    @Test
    void signedOutTokenSaysTheSessionEnded() throws Exception {
        TestUser alice = newUser("alice");
        mockMvc.perform(post("/api/vi/auth/logout").with(bearer(alice))).andExpect(status().isNoContent());

        assertError(mockMvc.perform(get("/api/vi/auth/me").with(bearer(alice))),
                401, "Unauthorized", "Invalid token: this session has been signed out, log in again", "/api/vi/auth/me");
    }

    @Test
    void wrongPasswordDoesNotRevealWhetherTheUserExists() throws Exception {
        TestUser alice = newUser("alice");

        assertError(mockMvc.perform(post("/api/vi/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(alice.username(), "wrong"))),
                401, "Unauthorized", "Invalid username or password", "/api/vi/auth/login");
        assertError(mockMvc.perform(post("/api/vi/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(uniqueName("nobody"), "wrong"))),
                401, "Unauthorized", "Invalid username or password", "/api/vi/auth/login");
    }

    // --- 403 --------------------------------------------------------------------------------

    @Test
    void missingPermissionNamesTheRequiredRule() throws Exception {
        TestUser alice = newUser("alice");

        assertError(mockMvc.perform(get("/api/vi/user").with(bearer(alice))),
                403, "Forbidden", "Access denied: this action requires hasAuthority('user:read')", "/api/vi/user");
    }

    @Test
    void likingAsSomeoneElseExplainsTheRule() throws Exception {
        TestUser alice = newUser("alice");
        TestUser bob = newUser("bob");
        long postId = createPost(alice, "Post");
        String path = "/api/vi/user/%d/likes/%d".formatted(bob.id(), postId);

        assertError(mockMvc.perform(post(path).with(bearer(alice))), 403, "Forbidden",
                "Access denied: you can only like or unlike posts as yourself (your user id is %d)".formatted(alice.id()),
                path);
    }

    @Test
    void deletingSomeoneElsesPostExplainsTheRule() throws Exception {
        TestUser alice = newUser("alice");
        TestUser bob = newUser("bob");
        long postId = createPost(alice, "Post");

        assertError(mockMvc.perform(delete("/api/vi/post/{id}", postId).with(bearer(bob))), 403, "Forbidden",
                "Access denied: you can only delete your own posts unless you have post:moderate",
                "/api/vi/post/" + postId);
    }

    // --- 404 --------------------------------------------------------------------------------

    @Test
    void unknownRecordNamesTheResourceAndId() throws Exception {
        TestUser alice = newUser("alice");

        assertError(mockMvc.perform(delete("/api/vi/post/{id}", 999_999_999L).with(bearer(alice))),
                404, "Not Found", "Post with id 999999999 not found", "/api/vi/post/999999999");
        assertError(mockMvc.perform(put("/api/vi/user/{id}/roles", 999_999_999L)
                        .with(bearer(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"USER\"]}")),
                404, "Not Found", "User with id 999999999 not found", "/api/vi/user/999999999/roles");
    }

    @Test
    void unknownEndpointIsReported() throws Exception {
        TestUser alice = newUser("alice");

        assertError(mockMvc.perform(get("/api/vi/nothing-here").with(bearer(alice))),
                404, "Not Found", "No endpoint GET /api/vi/nothing-here", "/api/vi/nothing-here");
    }

    // --- 400 --------------------------------------------------------------------------------

    @Test
    void validationErrorsListEachField() throws Exception {
        ResultActions result = mockMvc.perform(post("/api/vi/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"\",\"password\":\"\"}"));

        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", allOf(
                        startsWith("Validation failed: "),
                        containsString("'username' must not be blank"),
                        containsString("'password' must not be blank"))))
                .andExpect(jsonPath("$.fieldErrors.username").value("must not be blank"))
                .andExpect(jsonPath("$.fieldErrors.password").value("must not be blank"));
    }

    @Test
    void malformedJsonIsReported() throws Exception {
        assertError(mockMvc.perform(post("/api/vi/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json")),
                400, "Bad Request", "Request body is missing or is not valid JSON, or a field has the wrong type",
                "/api/vi/auth/register");
    }

    @Test
    void wrongPathVariableTypeNamesTheParameter() throws Exception {
        TestUser alice = newUser("alice");

        assertError(mockMvc.perform(delete("/api/vi/post/abc").with(bearer(alice))),
                400, "Bad Request", "Parameter 'postId' must be of type Long, but was 'abc'", "/api/vi/post/abc");
    }

    @Test
    void unknownRoleListsTheAvailableRoles() throws Exception {
        TestUser alice = newUser("alice");

        mockMvc.perform(put("/api/vi/user/{id}/roles", alice.id())
                        .with(bearer(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"NOPE\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", allOf(
                        startsWith("Unknown roles: [NOPE]. Available roles: ["),
                        containsString("ADMIN"), containsString("MODERATOR"), containsString("USER"))));
    }

    @Test
    void unknownPermissionListsTheAvailablePermissions() throws Exception {
        mockMvc.perform(post("/api/vi/role")
                        .with(bearer(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleName\":\"%s\",\"permissions\":[\"no:such\"]}".formatted(uniqueName("role"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", allOf(
                        startsWith("Unknown permissions: [no:such]. Available permissions: ["),
                        containsString("post:read"), containsString("role:write"))));
    }

    // --- 405 / 409 / 415 --------------------------------------------------------------------

    @Test
    void unsupportedMethodListsTheSupportedOnes() throws Exception {
        TestUser alice = newUser("alice");

        mockMvc.perform(patch("/api/vi/post").with(bearer(alice)))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.message", allOf(
                        startsWith("Method PATCH is not supported for this endpoint. Supported methods: "),
                        containsString("GET"), containsString("POST"))));
    }

    @Test
    void duplicateUsernameNamesTheUsername() throws Exception {
        TestUser alice = newUser("alice");

        assertError(mockMvc.perform(post("/api/vi/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(alice.username(), "other"))),
                409, "Conflict", "Username '%s' is already taken".formatted(alice.username()), "/api/vi/auth/register");
    }

    @Test
    void duplicateRoleNamesTheRole() throws Exception {
        assertError(mockMvc.perform(post("/api/vi/role")
                        .with(bearer(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleName\":\"admin\"}")),
                409, "Conflict", "Role 'ADMIN' already exists", "/api/vi/role");
    }

    @Test
    void wrongContentTypeAsksForJson() throws Exception {
        // the message repeats the header as sent; MockMvc adds ";charset=UTF-8"
        mockMvc.perform(post("/api/vi/auth/register")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("alice"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message", allOf(
                        startsWith("Content type 'text/plain"),
                        endsWith("is not supported. Send the body as application/json"))));
    }

    @Test
    void postTitleAndContentAreRequiredAndLimitedToTheColumnSize() throws Exception {
        TestUser alice = newUser("alice");

        mockMvc.perform(post("/api/vi/post").with(bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"content\":\"%s\"}".formatted("x".repeat(256))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").value("must not be blank"))
                .andExpect(jsonPath("$.fieldErrors.content").value("size must be between 0 and 255"));
    }

    @Test
    void passwordLongerThanBcryptSupportsIsRejected() throws Exception {
        mockMvc.perform(post("/api/vi/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(uniqueName("alice"), "x".repeat(73))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").value("size must be between 0 and 72"));
    }
}
