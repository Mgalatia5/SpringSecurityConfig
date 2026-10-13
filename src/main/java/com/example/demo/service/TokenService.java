package com.example.demo.service;

import com.example.demo.config.AppProperties;
import com.example.demo.dao.RefreshTokenRepository;
import com.example.demo.dto.res.TokenResponse;
import com.example.demo.exception.ApiException;
import com.example.demo.model.RefreshToken;
import com.example.demo.model.UserAccount;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

/**
 * Login sessions. A login starts a session with a short-lived access token (JWT) and a single-use
 * refresh token. Each refresh replaces the refresh token; presenting a used one again means it was
 * probably copied, so the whole session is signed out. A signed-out session's access tokens are
 * rejected on the next request ({@link #isSessionActive}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {
    /** Access token claim holding the session id. */
    public static final String SESSION_CLAIM = "sid";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JwtEncoder jwtEncoder;
    private final RefreshTokenRepository refreshTokens;
    private final AppProperties properties;

    @Transactional
    public TokenResponse login(UserAccount user) {
        return issueTokens(user, UUID.randomUUID().toString());
    }

    @Transactional(noRollbackFor = ApiException.class)
    public TokenResponse refresh(String rawRefreshToken) {
        RefreshToken token = refreshTokens.findByTokenHash(hash(rawRefreshToken))
                .orElseThrow(() -> mustLogIn("Invalid refresh token"));
        if (token.getUsedAt() != null) {
            refreshTokens.revokeSession(token.getSessionId());   // committed despite the exception (noRollbackFor)
            log.warn("Refresh token reuse for user {}: session {} signed out",
                    token.getUser().getUsername(), token.getSessionId());
            throw mustLogIn("Refresh token has already been used. For safety, this session has been signed out");
        }
        if (token.isRevoked()) {
            throw mustLogIn("Refresh token has been revoked");
        }
        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw mustLogIn("Refresh token has expired");
        }
        if (!token.getUser().isEnabled()) {
            throw new DisabledException("Account is disabled");
        }
        token.setUsedAt(Instant.now());
        return issueTokens(token.getUser(), token.getSessionId());
    }

    /** Signs out one session: its access and refresh tokens stop working. */
    @Transactional
    public void logout(Jwt accessToken) {
        refreshTokens.revokeSession(accessToken.getClaimAsString(SESSION_CLAIM));
    }

    /** Signs out every session of the user, on all devices. */
    @Transactional
    public void logoutAll(Long userId) {
        refreshTokens.revokeAllForUser(userId);
    }

    /** Checked on every request. Tokens without a session id are rejected. */
    @Transactional(readOnly = true)
    public boolean isSessionActive(Jwt accessToken) {
        String sessionId = accessToken.getClaimAsString(SESSION_CLAIM);
        return sessionId != null && !refreshTokens.existsBySessionIdAndRevokedTrue(sessionId);
    }

    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.HOURS)
    @Transactional
    public void deleteExpiredRefreshTokens() {
        refreshTokens.deleteExpired(Instant.now());
    }

    private TokenResponse issueTokens(UserAccount user, String sessionId) {
        Instant now = Instant.now();
        Duration accessTtl = properties.jwt().accessTokenTtl();
        Duration refreshTtl = properties.jwt().refreshTokenTtl();

        String refreshToken = randomToken();
        refreshTokens.save(RefreshToken.builder().tokenHash(hash(refreshToken)).sessionId(sessionId)
                .user(user).expiresAt(now.plus(refreshTtl)).build());

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .id(UUID.randomUUID().toString())
                .issuer("demo")
                .subject(user.getUsername())
                .issuedAt(now)
                .expiresAt(now.plus(accessTtl))
                .claim(SESSION_CLAIM, sessionId)
                .build();
        String accessToken = jwtEncoder.encode(
                JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

        return new TokenResponse(accessToken, "Bearer", accessTtl.toSeconds(), refreshToken, refreshTtl.toSeconds());
    }

    private static ApiException mustLogIn(String reason) {
        return new ApiException(UNAUTHORIZED, reason + ": log in again");
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
