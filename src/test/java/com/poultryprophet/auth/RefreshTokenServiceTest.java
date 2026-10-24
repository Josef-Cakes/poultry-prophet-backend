package com.poultryprophet.auth;

import com.poultryprophet.common.BadRequestException;
import com.poultryprophet.config.SecurityProperties;
import com.poultryprophet.user.User;
import com.poultryprophet.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock private AuthRefreshTokenRepository tokenRepository;
    @Mock private UserRepository userRepository;

    @Test
    void issuesAHighEntropyTokenAndStoresOnlyItsHash() {
        SecurityProperties properties = properties();
        RefreshTokenService service = new RefreshTokenService(tokenRepository, userRepository, properties);
        User user = user(7L);

        ArgumentCaptor<AuthRefreshToken> saved = ArgumentCaptor.forClass(AuthRefreshToken.class);
        when(tokenRepository.save(saved.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        RefreshTokenService.IssuedToken issued = service.issue(user);

        assertThat(issued.rawToken()).hasSizeGreaterThan(80);
        assertThat(issued.expiresAt()).isAfter(Instant.now());
        assertThat(saved.getValue().getUserId()).isEqualTo(7L);
        assertThat(saved.getValue().getTokenHash()).isNotEqualTo(issued.rawToken());
        assertThat(saved.getValue().getTokenHash()).hasSize(43);
    }

    @Test
    void rotatesAValidTokenAndRevokesThePreviousCredential() {
        RefreshTokenService service = new RefreshTokenService(tokenRepository, userRepository, properties());
        AuthRefreshToken stored = storedToken(7L, Instant.now().plusSeconds(60));
        User user = user(7L);
        when(tokenRepository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(stored));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(tokenRepository.save(any(AuthRefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RefreshTokenService.Rotation rotation = service.rotate("known-refresh-token");

        assertThat(rotation.user()).isSameAs(user);
        assertThat(rotation.rawToken()).hasSizeGreaterThan(80);
        assertThat(stored.getRevokedAt()).isNotNull();
        verify(tokenRepository, org.mockito.Mockito.times(2)).save(any(AuthRefreshToken.class));
    }

    @Test
    void revokesAllSessionsWhenAPreviouslyRotatedTokenIsReused() {
        RefreshTokenService service = new RefreshTokenService(tokenRepository, userRepository, properties());
        AuthRefreshToken reused = storedToken(7L, Instant.now().plusSeconds(60));
        reused.revoke(Instant.now().minusSeconds(1));
        AuthRefreshToken otherSession = storedToken(7L, Instant.now().plusSeconds(60));
        when(tokenRepository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(reused));
        when(tokenRepository.findByUserIdAndRevokedAtIsNull(7L)).thenReturn(List.of(otherSession));
        when(tokenRepository.save(any(AuthRefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> service.rotate("reused-token"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already been used");
        assertThat(otherSession.getRevokedAt()).isNotNull();
    }

    private static SecurityProperties properties() {
        SecurityProperties properties = new SecurityProperties();
        properties.setRefreshTokenExpirationMs(2_592_000_000L);
        return properties;
    }

    private static AuthRefreshToken storedToken(Long userId, Instant expiresAt) {
        AuthRefreshToken token = new AuthRefreshToken();
        token.setUserId(userId);
        token.setTokenHash("hash");
        token.setExpiresAt(expiresAt);
        return token;
    }

    private static User user(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}
