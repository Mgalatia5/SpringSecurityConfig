package com.example.demo.service;

import com.example.demo.config.security.SessionValidator;
import com.example.demo.dao.AuthRepositories.RefreshTokenRepository;
import com.example.demo.dto.AuthDto.TokenResponse;
import com.example.demo.exception.ApiException;
import com.example.demo.model.RefreshToken;
import com.example.demo.model.UserAccount;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static com.example.demo.service.TokenService.SESSION_CLAIM;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

/**
 * Login sessions: starting, refreshing and signing out.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService implements SessionValidator {
    private final RefreshTokenRepository refreshTokens;
    private final TokenService tokens;

    @Transactional
    public TokenResponse login(UserAccount user) {
        return tokens.issue(user, UUID.randomUUID().toString());
    }

    @Transactional(noRollbackFor = ApiException.class)
    public TokenResponse refresh(String rawRefreshToken) {
        RefreshToken token = refreshTokens.findByTokenHash(TokenService.hash(rawRefreshToken))
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
        return tokens.issue(token.getUser(), token.getSessionId());
    }

    /**
     * Signs out one session: its access and refresh tokens stop working.
     */
    @Transactional
    public void logout(Jwt accessToken) {
        refreshTokens.revokeSession(accessToken.getClaimAsString(SESSION_CLAIM));
    }

    /**
     * Signs out every session of the user, on all devices.
     */
    @Transactional
    public void logoutAll(Long userId) {
        refreshTokens.revokeAllForUser(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isSessionActive(Jwt accessToken) {
        String sessionId = accessToken.getClaimAsString(SESSION_CLAIM);
        return sessionId != null && !refreshTokens.existsBySessionIdAndRevokedTrue(sessionId);
    }

    private static ApiException mustLogIn(String reason) {
        return new ApiException(UNAUTHORIZED, reason + ": log in again");
    }
}
