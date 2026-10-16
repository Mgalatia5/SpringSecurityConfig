package com.example.demo.controller;

import com.example.demo.IntegrationTestSupport;
import com.example.demo.dao.AuthRepositories.RefreshTokenRepository;
import com.example.demo.dao.AuthRepositories.UserRepository;
import com.example.demo.model.UserAccount;
import com.example.demo.service.TokenService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RefreshTokenTest extends IntegrationTestSupport {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JwtEncoder jwtEncoder;

    private record Tokens(String accessToken, String refreshToken) {
        TestUser as(TestUser user) {
            return new TestUser(user.id(), user.username(), accessToken);
        }
    }

    private static Tokens tokens(String json) {
        return new Tokens(JsonPath.read(json, "$.accessToken"), JsonPath.read(json, "$.refreshToken"));
    }

    private Tokens loginTokens(String username) throws Exception {
        return tokens(body(mockMvc.perform(post("/api/vi/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(username, PASSWORD)))
                .andExpect(status().isOk())));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/vi/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"%s\"}".formatted(refreshToken)));
    }

    private Tokens refreshOk(String refreshToken) throws Exception {
        return tokens(body(refresh(refreshToken).andExpect(status().isOk())));
    }

    @Test
    void loginReturnsAccessAndRefreshTokens() throws Exception {
        String username = uniqueName("alice");
        register(username);

        mockMvc.perform(post("/api/vi/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(username, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshExpiresIn").value(Duration.ofDays(7).toSeconds()));
    }

    @Test
    void refreshTokenIsStoredOnlyAsHash() throws Exception {
        String username = uniqueName("alice");
        register(username);
        Tokens tokens = loginTokens(username);

        assertThat(refreshTokenRepository.findAll())
                .noneMatch(stored -> stored.getTokenHash().equals(tokens.refreshToken()));
    }

    @Test
    void refreshWorksAfterTheAccessTokenHasExpired() throws Exception {
        TestUser alice = newUser("alice");
        Tokens tokens = loginTokens(alice.username());
        String sessionId = JsonPath.read(new String(java.util.Base64.getUrlDecoder()
                .decode(tokens.accessToken().split("\\.")[1])), "$.sid");

        // an access token for the same session that expired 5 minutes ago (beyond the 60s clock skew)
        Instant past = Instant.now().minus(Duration.ofMinutes(20));
        String expired = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder()
                        .id(UUID.randomUUID().toString())
                        .subject(alice.username())
                        .issuedAt(past)
                        .expiresAt(past.plus(Duration.ofMinutes(15)))
                        .claim(TokenService.SESSION_CLAIM, sessionId)
                        .build())).getTokenValue();
        mockMvc.perform(get("/api/vi/auth/me").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", allOf(startsWith("Invalid token: "), containsString("expired"))));

        // no login needed: the refresh token gets a new, working access token
        Tokens renewed = refreshOk(tokens.refreshToken());
        mockMvc.perform(get("/api/vi/auth/me").with(bearer(renewed.as(alice))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(alice.username()));
    }

    @Test
    void refreshRotatesTheRefreshToken() throws Exception {
        TestUser alice = newUser("alice");
        Tokens first = loginTokens(alice.username());

        Tokens second = refreshOk(first.refreshToken());
        Tokens third = refreshOk(second.refreshToken());

        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
        assertThat(third.refreshToken()).isNotEqualTo(second.refreshToken());
        assertThat(second.accessToken()).isNotEqualTo(first.accessToken());
        mockMvc.perform(get("/api/vi/auth/me").with(bearer(third.as(alice))))
                .andExpect(status().isOk());
    }

    @Test
    void reusingARefreshTokenSignsOutTheWholeSession() throws Exception {
        TestUser alice = newUser("alice");
        Tokens first = loginTokens(alice.username());
        Tokens second = refreshOk(first.refreshToken());

        // the old token is presented again, e.g. by someone who copied it
        refresh(first.refreshToken())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(
                        "Refresh token has already been used. For safety, this session has been signed out: log in again"));

        // the newest refresh token of that session stops working too
        refresh(second.refreshToken())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Refresh token has been revoked: log in again"));
    }

    @Test
    void reuseOnlyAffectsThatSession() throws Exception {
        TestUser alice = newUser("alice");
        Tokens laptop = loginTokens(alice.username());
        Tokens phone = loginTokens(alice.username());

        refreshOk(laptop.refreshToken());
        refresh(laptop.refreshToken()).andExpect(status().isUnauthorized());

        refreshOk(phone.refreshToken());
    }

    @Test
    void logoutRevokesTheSessionsRefreshToken() throws Exception {
        TestUser alice = newUser("alice");
        Tokens laptop = loginTokens(alice.username());
        Tokens phone = loginTokens(alice.username());

        mockMvc.perform(post("/api/vi/auth/logout").with(bearer(laptop.as(alice))))
                .andExpect(status().isNoContent());

        refresh(laptop.refreshToken())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Refresh token has been revoked: log in again"));
        refreshOk(phone.refreshToken());
    }

    @Test
    void logoutAfterARefreshStillEndsTheSession() throws Exception {
        TestUser alice = newUser("alice");
        Tokens renewed = refreshOk(loginTokens(alice.username()).refreshToken());

        mockMvc.perform(post("/api/vi/auth/logout").with(bearer(renewed.as(alice))))
                .andExpect(status().isNoContent());

        refresh(renewed.refreshToken()).andExpect(status().isUnauthorized());
    }

    @Test
    void expiredRefreshTokenIsRejected() throws Exception {
        TestUser alice = newUser("alice");
        Tokens tokens = loginTokens(alice.username());
        refreshTokenRepository.findAll().stream()
                .filter(token -> token.getUser().getId().equals(alice.id()))
                .forEach(token -> {
                    token.setExpiresAt(Instant.now().minusSeconds(1));
                    refreshTokenRepository.save(token);
                });

        refresh(tokens.refreshToken())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Refresh token has expired: log in again"));
    }

    @Test
    void disabledUserCannotRefresh() throws Exception {
        TestUser alice = newUser("alice");
        Tokens tokens = loginTokens(alice.username());
        UserAccount account = userRepository.findByUsername(alice.username()).orElseThrow();
        account.setEnabled(false);
        userRepository.save(account);

        refresh(tokens.refreshToken())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Account is disabled"));
    }

    @Test
    void unknownRefreshTokenIsRejected() throws Exception {
        refresh("made-up-token")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid refresh token: log in again"));
    }

    @Test
    void accessTokenCannotBeUsedAsRefreshToken() throws Exception {
        TestUser alice = newUser("alice");

        refresh(alice.token()).andExpect(status().isUnauthorized());
    }

    @Test
    void missingRefreshTokenIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/vi/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.refreshToken").value("must not be blank"));
    }

    // --- Access tokens of a signed-out session --------------------------------------------

    private void assertSignedOut(TestUser user) throws Exception {
        mockMvc.perform(get("/api/vi/auth/me").with(bearer(user)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid token: this session has been signed out, log in again"));
    }

    private void assertActive(TestUser user) throws Exception {
        mockMvc.perform(get("/api/vi/auth/me").with(bearer(user))).andExpect(status().isOk());
    }

    @Test
    void refreshTokenReuseAlsoRejectsTheSessionsAccessTokens() throws Exception {
        TestUser alice = newUser("alice");
        Tokens first = loginTokens(alice.username());
        Tokens second = refreshOk(first.refreshToken());   // e.g. the attacker refreshed with a stolen token
        assertActive(first.as(alice));
        assertActive(second.as(alice));

        refresh(first.refreshToken()).andExpect(status().isUnauthorized());   // the real user's stale token

        // every access token of that session stops working at once, not when it expires
        assertSignedOut(first.as(alice));
        assertSignedOut(second.as(alice));
    }

    @Test
    void reuseLeavesOtherSessionsAccessTokensWorking() throws Exception {
        TestUser alice = newUser("alice");
        Tokens laptop = loginTokens(alice.username());
        Tokens phone = loginTokens(alice.username());

        refreshOk(laptop.refreshToken());
        refresh(laptop.refreshToken()).andExpect(status().isUnauthorized());

        assertSignedOut(laptop.as(alice));
        assertActive(phone.as(alice));
    }

    @Test
    void logoutRejectsEveryAccessTokenOfTheSession() throws Exception {
        TestUser alice = newUser("alice");
        Tokens first = loginTokens(alice.username());
        Tokens second = refreshOk(first.refreshToken());

        // logging out with the newest access token also ends the older one from the same session
        mockMvc.perform(post("/api/vi/auth/logout").with(bearer(second.as(alice))))
                .andExpect(status().isNoContent());

        assertSignedOut(first.as(alice));
        assertSignedOut(second.as(alice));
    }

    @Test
    void accessTokenWithoutSessionIsRejected() throws Exception {
        TestUser alice = newUser("alice");
        Instant now = Instant.now();
        String noSession = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder()
                        .id(UUID.randomUUID().toString())
                        .subject(alice.username())
                        .issuedAt(now)
                        .expiresAt(now.plus(Duration.ofMinutes(15)))
                        .build())).getTokenValue();

        assertSignedOut(new TestUser(alice.id(), alice.username(), noSession));
    }

    // --- Log out from all devices ---------------------------------------------------------

    @Test
    void logoutAllSignsOutEverySessionOfTheUser() throws Exception {
        TestUser alice = newUser("alice");
        Tokens laptop = loginTokens(alice.username());
        Tokens phone = loginTokens(alice.username());
        Tokens tablet = refreshOk(loginTokens(alice.username()).refreshToken());

        mockMvc.perform(post("/api/vi/auth/logout-all").with(bearer(phone.as(alice))))
                .andExpect(status().isNoContent());

        for (Tokens session : new Tokens[]{laptop, phone, tablet}) {
            assertSignedOut(session.as(alice));
            refresh(session.refreshToken())
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Refresh token has been revoked: log in again"));
        }
        assertSignedOut(alice);   // the session created by newUser() too
    }

    @Test
    void logoutAllDoesNotAffectOtherUsers() throws Exception {
        TestUser alice = newUser("alice");
        TestUser bob = newUser("bob");
        Tokens bobSession = loginTokens(bob.username());

        mockMvc.perform(post("/api/vi/auth/logout-all").with(bearer(alice)))
                .andExpect(status().isNoContent());

        assertActive(bob);
        refreshOk(bobSession.refreshToken());
    }

    @Test
    void userCanLogInAgainAfterLogoutAll() throws Exception {
        TestUser alice = newUser("alice");
        mockMvc.perform(post("/api/vi/auth/logout-all").with(bearer(alice)))
                .andExpect(status().isNoContent());

        Tokens fresh = loginTokens(alice.username());
        assertActive(fresh.as(alice));
        refreshOk(fresh.refreshToken());
    }

    @Test
    void logoutAllRequiresAValidToken() throws Exception {
        mockMvc.perform(post("/api/vi/auth/logout-all"))
                .andExpect(status().isUnauthorized());
    }
}
