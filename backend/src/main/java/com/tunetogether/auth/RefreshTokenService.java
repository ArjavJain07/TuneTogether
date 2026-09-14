package com.tunetogether.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Opaque, server-tracked refresh tokens (deliberately not JWTs - a self-issued
 * JWT refresh token can't be revoked without a server-side blocklist anyway, so
 * there's no benefit over a plain hashed-at-rest random token, and the latter is
 * simpler to reason about).
 * <p>
 * Rotated on every use: presenting a refresh token always invalidates it and
 * issues a new one. Presenting an already-revoked token (replay of a stolen
 * token after the legitimate client already rotated past it) revokes every
 * refresh token belonging to that user as a compromise response.
 */
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final Duration refreshTokenTtl;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            @Value("${app.jwt.refresh-token-ttl-days}") long refreshTokenTtlDays) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenTtl = Duration.ofDays(refreshTokenTtlDays);
    }

    @Transactional
    public String issueFor(AppUser user) {
        String rawToken = randomToken();
        RefreshToken entity = new RefreshToken(user, sha256(rawToken), Instant.now().plus(refreshTokenTtl));
        refreshTokenRepository.save(entity);
        return rawToken;
    }

    /** Validates and rotates a refresh token, returning the resulting user and the new raw token. */
    @Transactional
    public RotationResult rotate(String rawToken) {
        RefreshToken existing = refreshTokenRepository.findByTokenHash(sha256(rawToken))
                .orElseThrow(() -> new InvalidTokenException("Unknown refresh token"));

        if (existing.isRevoked()) {
            // Reuse of an already-rotated-away token: treat as a compromise signal.
            refreshTokenRepository.revokeAllForUser(existing.getUser().getId());
            throw new InvalidTokenException("Refresh token reuse detected - all sessions revoked");
        }
        if (!existing.isUsable()) {
            throw new InvalidTokenException("Refresh token expired");
        }

        existing.revoke();
        String newRawToken = issueFor(existing.getUser());
        return new RotationResult(existing.getUser(), newRawToken);
    }

    @Transactional
    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHash(sha256(rawToken)).ifPresent(RefreshToken::revoke);
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public record RotationResult(AppUser user, String rawRefreshToken) {
    }
}
