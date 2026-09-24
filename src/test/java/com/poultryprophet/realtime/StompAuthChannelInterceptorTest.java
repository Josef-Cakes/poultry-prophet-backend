package com.poultryprophet.realtime;

import com.poultryprophet.security.JwtService;
import com.poultryprophet.user.User;
import com.poultryprophet.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StompAuthChannelInterceptorTest {

    @Mock private JwtService jwtService;
    @Mock private UserRepository userRepository;

    private StompAuthChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new StompAuthChannelInterceptor(jwtService, userRepository);
    }

    @Test
    void rejectsConnectWithoutBearerToken() {
        assertThat(interceptor.preSend(frame(StompCommand.CONNECT, null, null), null)).isNull();
    }

    @Test
    void rejectsInvalidAndCrossFarmSubscriptionsAndSends() {
        Message<byte[]> connect = frame(StompCommand.CONNECT, "Bearer token", null);
        User user = new User();
        user.setEmail("handler@example.test");
        user.setFarmId(7L);
        when(jwtService.isValid("token")).thenReturn(true);
        when(jwtService.extractEmail("token")).thenReturn(user.getEmail());
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        Message<?> authenticated = interceptor.preSend(connect, null);
        StompHeaderAccessor connectAccessor = StompHeaderAccessor.wrap(authenticated);

        assertThat(interceptor.preSend(withPrincipal(StompCommand.SUBSCRIBE,
                "/topic/farms/8/alerts", connectAccessor.getUser()), null)).isNull();
        assertThat(interceptor.preSend(withPrincipal(StompCommand.SEND,
                "/app/farms/8/events", connectAccessor.getUser()), null)).isNull();
        assertThat(interceptor.preSend(withPrincipal(StompCommand.SUBSCRIBE,
                "/topic/farms/7/alerts", connectAccessor.getUser()), null)).isNotNull();
        assertThat(interceptor.preSend(withPrincipal(StompCommand.SEND,
                "/app/farms/7/events", connectAccessor.getUser()), null)).isNotNull();
    }

    private static Message<byte[]> frame(StompCommand command, String authorization, String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (authorization != null) {
            accessor.addNativeHeader("Authorization", authorization);
        }
        if (destination != null) {
            accessor.setDestination(destination);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private static Message<byte[]> withPrincipal(StompCommand command, String destination,
                                                   java.security.Principal principal) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setDestination(destination);
        accessor.setUser(principal);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
