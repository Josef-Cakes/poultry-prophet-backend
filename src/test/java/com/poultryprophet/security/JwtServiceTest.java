package com.poultryprophet.security;

import com.poultryprophet.config.SecurityProperties;
import com.poultryprophet.user.Role;
import com.poultryprophet.user.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import javax.crypto.SecretKey;
import java.util.Date;
import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {
    private final SecretKey key = Keys.hmacShaKeyFor(new byte[32]);
    private JwtService service() {
        SecurityProperties p = new SecurityProperties();
        p.setJwtSecret(Encoders.BASE64.encode(key.getEncoded()));
        return new JwtService(p);
    }
    @Test void usesImmutableUserIdAsSubject() {
        User user = new User();
        user.setId(12L);
        user.setEmail("before@example.test");
        user.setRole(Role.MANAGER);
        String token = service().generateToken(user);
        assertThat(Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject())
                .isEqualTo("12");
    }
    @Test void rejectsLegacyEmailSubjectEvenIfSignedAndUnexpired() {
        String legacy = Jwts.builder().subject("before@example.test").claim("uid", 12)
                .expiration(new Date(System.currentTimeMillis() + 60000)).signWith(key).compact();
        assertThat(service().isValid(legacy)).isFalse();
    }
    @Test void rejectsSignedTokenWithNoExpiration() {
        String token = Jwts.builder().subject("12").claim("credentialVersion", 0).signWith(key).compact();
        assertThat(service().isValid(token)).isFalse();
    }
}
