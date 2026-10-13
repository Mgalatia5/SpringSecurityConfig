package com.example.demo.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * A refresh token. Only its SHA-256 hash is stored, never the token itself.
 * All tokens issued from one login share a {@code sessionId}.
 */
@Entity
@Table(name = "refresh_tokens", indexes = {
        @Index(name = "idx_refresh_tokens_session_id", columnList = "sessionId"),
        @Index(name = "idx_refresh_tokens_user_id", columnList = "user_id")})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String tokenHash;

    @Column(nullable = false)
    private String sessionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private UserAccount user;

    private Instant expiresAt;

    /** Set when exchanged for a new token. A used token is never accepted again. */
    private Instant usedAt;

    /** Set for the whole session on logout, or when a used token is presented again. */
    private boolean revoked;
}
