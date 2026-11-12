package com.poultryprophet.user;

import com.poultryprophet.auth.RefreshTokenService;
import com.poultryprophet.common.BadRequestException;
import com.poultryprophet.farm.FarmRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserIdentityTest {
    @Test void passwordChangeInvalidatesCredentialsAndRevokesRefreshSessions() {
        UserRepository users = mock(UserRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        RefreshTokenService sessions = mock(RefreshTokenService.class);
        User user = new User(); user.setId(1L); user.setPasswordHash("old-hash");
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(encoder.matches("old", "old-hash")).thenReturn(true);
        when(encoder.encode("new")).thenReturn("new-hash");
        new UserService(users, encoder, mock(FarmRepository.class), sessions).changePassword(1L, "old", "new");
        assertThat(user.getCredentialVersion()).isEqualTo(1);
        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        verify(sessions).revokeAllForUser(1L);
    }
    @Test void wrongPasswordDoesNotRevokeSessions() {
        UserRepository users = mock(UserRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        RefreshTokenService sessions = mock(RefreshTokenService.class);
        User user = new User(); user.setPasswordHash("old-hash");
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> new UserService(users, encoder, mock(FarmRepository.class), sessions)
                .changePassword(1L, "wrong", "new")).isInstanceOf(BadRequestException.class);
        assertThat(user.getCredentialVersion()).isZero();
        verifyNoInteractions(sessions);
    }
}
