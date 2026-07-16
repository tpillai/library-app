package com.library.security;

import com.library.config.JwtConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

// Builds and parses JWTs for AuthController (issuing) and JwtFilter (validating).
// Kept separate from JwtConfig so the config class stays a plain property holder.
@Component
public class JwtService {

    private final JwtConfig jwtConfig;
    private final SecretKey key;

    public JwtService(JwtConfig jwtConfig) {
        this.jwtConfig = jwtConfig;
        this.key = Keys.hmacShaKeyFor(jwtConfig.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String email, List<String> roles) {
        Instant now = Instant.now();
        Instant expiry = now.plus(jwtConfig.getTtlMinutes(), ChronoUnit.MINUTES);
        // sub = email, roles = granted role names, exp = now + ttl.
        // Anyone can read the JWT payload - never put a password in it.
        return Jwts.builder()
                .subject(email)
                .claim("roles", roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    public long getTtlSeconds() {
        return jwtConfig.getTtlMinutes() * 60L;
    }

    // Throws JwtException (unchecked) on a bad signature or expired token - the
    // caller (JwtFilter) turns that into a 401 rather than letting it propagate.
    public Claims parseClaims(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
