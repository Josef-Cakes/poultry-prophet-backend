package com.poultryprophet.security;

import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import com.poultryprophet.config.SecurityProperties;
import com.poultryprophet.user.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(SecurityProperties properties) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.getJwtSecret()));
        this.expirationMs = properties.getJwtExpirationMs();
    }

    public String generateToken(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("credentialVersion", user.getCredentialVersion())
                .claim("role", user.getRole().name())
                .claim("farmId", user.getFarmId())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    public TokenIdentity identity(String token) {
        Claims claims = parse(token);
        String subject = claims.getSubject();
        Object version = claims.get("credentialVersion");
        if (claims.getExpiration() == null || claims.getIssuedAt() == null
                || subject == null || !subject.matches("[1-9][0-9]*")
                || !(version instanceof Integer || version instanceof Long)) {
            throw new IllegalArgumentException("Invalid access token claims");
        }
        long userId = Long.parseLong(subject);
        long credentialVersion = ((Number) version).longValue();
        if (credentialVersion < 0) {
            throw new IllegalArgumentException("Invalid access token claims");
        }
        return new TokenIdentity(userId, credentialVersion);
    }

    public boolean isValid(String token) {
        try {
            identity(token);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    public record TokenIdentity(long userId, long credentialVersion) { }

    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
