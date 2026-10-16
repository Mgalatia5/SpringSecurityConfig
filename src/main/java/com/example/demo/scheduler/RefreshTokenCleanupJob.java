package com.example.demo.scheduler;

import com.example.demo.dao.AuthRepositories.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Runs every hour while the app is up: deletes refresh tokens that have expired.
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupJob {
    private final RefreshTokenRepository refreshTokens;

    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.HOURS)
    @Transactional
    public void deleteExpiredRefreshTokens() {
        refreshTokens.deleteExpired(Instant.now());
    }
}
