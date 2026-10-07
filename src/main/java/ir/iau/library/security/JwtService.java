package ir.iau.library.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * Issues and validates the JWT access tokens used by the API.
 *
 * <p>The signing secret is read from {@code app.security.jwt.secret} (env {@code JWT_SECRET}),
 * which the project already documents as the production secret. Tokens carry the subject
 * (username) and a single {@code role} claim. There is deliberately no refresh-token flow:
 * the app is a back-office with a single credential source today, and adding refresh tokens
 * without a real identity store would be speculative complexity.
 */
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationSeconds;

    public JwtService(
            @Value("${app.security.jwt.secret:}") String secret,
            @Value("${app.security.jwt.expiration:86400}") long expirationSeconds) {
        if (secret == null || secret.isBlank()) {
            // Fail closed: without a secret we cannot sign or verify anything, so the
            // app must not start rather than silently run unauthenticated.
            throw new IllegalStateException(
                    "app.security.jwt.secret (env JWT_SECRET) must be set; refusing to start without a signing key.");
        }
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "app.security.jwt.secret must be at least 32 bytes for HS256; generate one with `openssl rand -base64 48`.");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationSeconds = expirationSeconds;
    }

    /** Issue a signed token for an authenticated principal. */
    public String generateToken(String username, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationSeconds)))
                .signWith(signingKey)
                .compact();
    }

    /** Parse and verify a token, returning its claims; throws if invalid or expired. */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }
}
