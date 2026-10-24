package com.poultryprophet.auth;

import com.poultryprophet.common.BadRequestException;
import com.poultryprophet.config.SecurityProperties;
import com.poultryprophet.user.User;
import com.poultryprophet.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

@Service
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AuthRefreshTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final SecurityProperties securityProperties;

    public RefreshTokenService(AuthRefreshTokenRepository tokenRepository,
                               UserRepository userRepository,
                               SecurityProperties securityProperties) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.securityProperties = securityProperties;
    }

    @Transactional
    public IssuedToken issue(User user) {
        String rawToken = generateRawToken();
        Instant expiresAt = Instant.now().plusMillis(securityProperties.getRefreshTokenExpirationMs());

        AuthRefreshToken token = new AuthRefreshToken();
        token.setUserId(user.getId());
        token.setTokenHash(hash(rawToken));
        token.setExpiresAt(expiresAt);
        tokenRepository.save(token);
        return new IssuedToken(rawToken, expiresAt);
    }

    /**
     * Rotate a refresh token atomically. A second use of an already-rotated
     * token revokes the user's remaining refresh sessions as a token-reuse
     * response.
     */
    @Transactional
    public Rotation rotate(String rawToken) {
        AuthRefreshToken stored = tokenRepository.findByTokenHashForUpdate(hash(rawToken))
                .orElseThrow(() -> new BadRequestException("Invalid refresh token"));
        Instant now = Instant.now();

        if (stored.getRevokedAt() != null) {
            revokeAllForUser(stored.getUserId(), now);
            throw new BadRequestException("Refresh token has already been used or revoked");
        }
        if (!stored.getExpiresAt().isAfter(now)) {
            stored.revoke(now);
            tokenRepository.save(stored);
            throw new BadRequestException("Refresh token has expired");
        }

        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new BadRequestException("Refresh token owner no longer exists"));
        stored.revoke(now);
        tokenRepository.save(stored);
        IssuedToken replacement = issue(user);
        return new Rotation(user, replacement.rawToken(), replacement.expiresAt());
    }

    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        tokenRepository.findByTokenHashForUpdate(hash(rawToken)).ifPresent(token -> {
            if (token.getRevokedAt() == null) {
                token.revoke(Instant.now());
                tokenRepository.save(token);
            }
        });
    }

    @Transactional
    public void revokeAllForUser(Long userId) {
        revokeAllForUser(userId, Instant.now());
    }

    private void revokeAllForUser(Long userId, Instant now) {
        tokenRepository.findByUserIdAndRevokedAtIsNull(userId).forEach(token -> {
            token.revoke(now);
            tokenRepository.save(token);
        });
    }

    private String generateRawToken() {
        byte[] bytes = new byte[64];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BadRequestException("Refresh token is required");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    public record IssuedToken(String rawToken, Instant expiresAt) {
    }

    public record Rotation(User user, String rawToken, Instant expiresAt) {
    }
}
