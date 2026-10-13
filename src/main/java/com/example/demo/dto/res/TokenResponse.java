package com.example.demo.dto.res;

/**
 * @param expiresIn        seconds until the access token expires
 * @param refreshExpiresIn seconds until the refresh token expires
 */
public record TokenResponse(String accessToken, String tokenType, long expiresIn,
                            String refreshToken, long refreshExpiresIn) {
}
