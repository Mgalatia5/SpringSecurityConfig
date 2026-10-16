package com.example.demo.config.security;

import com.example.demo.dao.AuthRepositories.UserRepository;
import com.example.demo.model.UserAccount;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.servlet.HandlerExceptionResolver;

import static org.springframework.security.config.http.SessionCreationPolicy.STATELESS;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    private static final String[] PUBLIC_ENDPOINTS = {"/api/vi/auth/register", "/api/vi/auth/login", "/api/vi/auth/refresh"};

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, UserDetailsService users, SessionValidator sessions,
                                            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver)
            throws Exception {
        // Errors raised in the security filters get the same JSON body as every other error
        AuthenticationEntryPoint entryPoint = (request, response, ex) -> resolver.resolveException(request, response, null, ex);
        AccessDeniedHandler deniedHandler = (request, response, ex) -> resolver.resolveException(request, response, null, ex);
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST, PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(token -> authenticate(token, users, sessions))))
                .build();
    }

    /**
     * The token only identifies the user and session. Session, account status, roles and permissions
     * are checked against the database on every request, so changes take effect immediately.
     */
    private static UserJwtAuthenticationToken authenticate(Jwt jwt,
                                                           UserDetailsService users, SessionValidator sessions) {
        if (!sessions.isSessionActive(jwt)) {
            throw new InvalidBearerTokenException("this session has been signed out, log in again");
        }
        UserAccount user = (UserAccount) users.loadUserByUsername(jwt.getSubject());
        if (!user.isEnabled()) {
            throw new DisabledException("Account is disabled");
        }
        return new UserJwtAuthenticationToken(user, jwt);
    }

    @Bean
    UserDetailsService userDetailsService(UserRepository users) {
        return username -> users.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService users, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }
}
