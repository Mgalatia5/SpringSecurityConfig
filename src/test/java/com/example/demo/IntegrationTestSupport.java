package com.example.demo;

import com.jayway.jsonpath.JsonPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class for integration tests: the full app against a Testcontainers PostgreSQL, called
 * through MockMvc with real JWTs from the login endpoint. All subclasses share one Spring
 * context and one database, so every test creates users with unique names.
 */
@SpringBootTest(properties = {
        "app.admin.username=admin",
        "app.admin.password=admin-test-password",
        "app.jwt.secret=test-secret-0123456789abcdef0123456789abcdef",
        "spring.jpa.show-sql=false"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTestSupport {
    protected static final String PASSWORD = "secret";

    @Autowired
    protected MockMvc mockMvc;

    public record TestUser(long id, String username, String token) {
    }

    protected static String uniqueName(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    protected static String credentials(String username, String password) {
        return """
                {"username":"%s","password":"%s"}""".formatted(username, password);
    }

    protected static RequestPostProcessor bearer(TestUser user) {
        return request -> {
            request.addHeader("Authorization", "Bearer " + user.token());
            return request;
        };
    }

    protected String body(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    protected long register(String username) throws Exception {
        String response = body(mockMvc.perform(post("/api/vi/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(username, PASSWORD)))
                .andExpect(status().isOk()));
        return JsonPath.<Number>read(response, "$.id").longValue();
    }

    protected String login(String username, String password) throws Exception {
        String response = body(mockMvc.perform(post("/api/vi/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(username, password)))
                .andExpect(status().isOk()));
        return JsonPath.read(response, "$.accessToken");
    }

    /** Registers a new user (role USER) and logs them in. */
    protected TestUser newUser(String prefix) throws Exception {
        String username = uniqueName(prefix);
        long id = register(username);
        return new TestUser(id, username, login(username, PASSWORD));
    }

    protected TestUser admin() throws Exception {
        String token = login("admin", "admin-test-password");
        TestUser admin = new TestUser(0, "admin", token);
        String me = body(mockMvc.perform(get("/api/vi/auth/me").with(bearer(admin))).andExpect(status().isOk()));
        return new TestUser(JsonPath.<Number>read(me, "$.id").longValue(), "admin", token);
    }

    protected void assignRoles(TestUser user, String... roles) throws Exception {
        String roleList = String.join("\",\"", roles);
        mockMvc.perform(put("/api/vi/user/{id}/roles", user.id())
                        .with(bearer(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"" + roleList + "\"]}"))
                .andExpect(status().isOk());
    }

    protected long createPost(TestUser author, String title) throws Exception {
        String response = body(mockMvc.perform(post("/api/vi/post")
                        .with(bearer(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"%s\",\"content\":\"content\"}".formatted(title)))
                .andExpect(status().isOk()));
        return JsonPath.<Number>read(response, "$.id").longValue();
    }

    /** Ids of the users who liked the given post, as returned by GET /api/vi/post. */
    protected List<Long> likerIds(TestUser viewer, long postId) throws Exception {
        String response = body(mockMvc.perform(get("/api/vi/post").with(bearer(viewer))).andExpect(status().isOk()));
        List<Number> ids = JsonPath.read(response, "$[?(@.id == " + postId + ")].likedByUsers[*].id");
        return ids.stream().map(Number::longValue).toList();
    }
}
