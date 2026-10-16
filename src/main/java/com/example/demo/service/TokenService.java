package com.example.demo.service;

import com.example.demo.config.properties.JwtProperties;
import com.example.demo.dao.AuthRepositories.RefreshTokenRepository;
import com.example.demo.dto.AuthDto.TokenResponse;
import com.example.demo.model.RefreshToken;
import com.example.demo.model.UserAccount;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Creates access and refresh tokens. Refresh tokens are stored only as a SHA-256 hash.
 */
@Service
@RequiredArgsConstructor
public class TokenService {
    /**
     * Access token claim holding the session id.
     */
    public static final String SESSION_CLAIM = "sid";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokens;
    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;

    /** Must run inside the caller's transaction, as it saves the refresh token. */
    TokenResponse issue(UserAccount user, String sessionId) {
        Instant now = Instant.now();
        Duration accessTtl = jwtProperties.accessTokenTtl();
        Duration refreshTtl = jwtProperties.refreshTokenTtl();

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

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
