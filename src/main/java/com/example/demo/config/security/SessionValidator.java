package com.example.demo.config.security;

import org.springframework.security.oauth2.jwt.Jwt;

public interface SessionValidator {

    /** Checked on every request. Tokens without a session id are rejected. */
    boolean isSessionActive(Jwt accessToken);
}
