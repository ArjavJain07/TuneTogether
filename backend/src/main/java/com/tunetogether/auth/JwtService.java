package com.tunetogether.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * Issues and verifies stateless access-token JWTs. Access tokens are the single
 * bearer credential used both for REST calls (Authorization header) and for the
 * STOMP CONNECT frame - see ws.JwtStompChannelInterceptor.
 * <p>
 * Refresh tokens are deliberately NOT JWTs (see RefreshTokenService) - they're
 * opaque, server-tracked, and revocable, which a self-contained JWT can't be
 * without a server-side blocklist anyway.
 */
@Service
public class JwtService {

    private static final String CLAIM_APP_USER_ID = "uid";
    private static final String CLAIM_DISPLAY_NAME = "name";
    private static final String CLAIM_GUEST = "guest";
    private static final String CLAIM_ROLES = "roles";

    private final SecretKey signingKey;
    private final Duration accessTokenTtl;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-ttl-minutes}") long accessTokenTtlMinutes) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtl = Duration.ofMinutes(accessTokenTtlMinutes);
    }

    public String issueAccessToken(TuneTogetherPrincipal principal) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(principal.subjectId())
                .claim(CLAIM_DISPLAY_NAME, principal.displayName())
                .claim(CLAIM_GUEST, principal.guest())
                .claim(CLAIM_ROLES, principal.roles())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenTtl)));
        if (principal.appUserId() != null) {
            builder.claim(CLAIM_APP_USER_ID, principal.appUserId());
        }
        return builder.signWith(signingKey).compact();
    }

    public TuneTogetherPrincipal parseAndValidate(String token) {
        if (token == null || token.isBlank()) {
            throw new InvalidTokenException("Missing token");
        }
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new InvalidTokenException("Token expired", e);
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException("Invalid token", e);
        }

        Long appUserId = claims.get(CLAIM_APP_USER_ID, Long.class);
        String displayName = claims.get(CLAIM_DISPLAY_NAME, String.class);
        Boolean guest = claims.get(CLAIM_GUEST, Boolean.class);
        @SuppressWarnings("unchecked")
        List<String> roles = claims.get(CLAIM_ROLES, List.class);

        return new TuneTogetherPrincipal(
                claims.getSubject(),
                appUserId,
                displayName,
                Boolean.TRUE.equals(guest),
                roles == null ? List.of() : List.copyOf(roles));
    }
}
