package com.example.demo.config.security;

import com.example.demo.model.UserAccount;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Authentication of a request with a valid bearer token. Keeps the JWT (its session id is needed
 * for logout), which Spring's standard tokens would erase after authentication.
 */
public class UserJwtAuthenticationToken extends AbstractAuthenticationToken {
    private final UserAccount user;
    private final Jwt jwt;

    public UserJwtAuthenticationToken(UserAccount user, Jwt jwt) {
        super(user.getAuthorities());
        this.user = user;
        this.jwt = jwt;
        setAuthenticated(true);
    }

    @Override
    public UserAccount getPrincipal() {
        return user;
    }

    @Override
    public Jwt getCredentials() {
        return jwt;
    }
}
