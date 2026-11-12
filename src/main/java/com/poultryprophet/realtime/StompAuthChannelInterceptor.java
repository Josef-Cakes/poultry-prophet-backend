package com.poultryprophet.realtime;

import com.poultryprophet.security.JwtService;
import com.poultryprophet.user.User;
import com.poultryprophet.user.UserRepository;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.context.event.EventListener;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Authenticates the STOMP CONNECT frame using the same JWT as the REST API. The dashboard
 * sends {@code Authorization: Bearer <token>} as a STOMP connect header.
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final Map<String, FarmScopedPrincipal> sessions = new ConcurrentHashMap<>();
    private static final Pattern FARM_TOPIC =
            Pattern.compile("^/topic/farms/(\\d+)/(alerts|indicators)$");
    private static final Pattern FARM_APP_DESTINATION =
            Pattern.compile("^/app/farms/(\\d+)(?:/.*)?$");

    public StompAuthChannelInterceptor(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authorization = accessor.getFirstNativeHeader("Authorization");
            if (authorization == null || !authorization.startsWith("Bearer ")) {
                return null;
            }
            String token = authorization.substring(7);
            User user = authenticatedUser(token);
            if (user == null || user.getFarmId() == null) {
                return null;
            }
            FarmScopedPrincipal principal = new FarmScopedPrincipal(user.getEmail(), user.getFarmId(), token);
            accessor.setUser(principal);
            if (accessor.getSessionId() != null) sessions.put(accessor.getSessionId(), principal);
        }

        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            if (!isOwnedFarmDestination(accessor, FARM_TOPIC)) {
                return null;
            }
        }

        if (StompCommand.SEND.equals(accessor.getCommand())
                && !isOwnedFarmDestination(accessor, FARM_APP_DESTINATION)) {
            return null;
        }
        return message;
    }

    public Message<?> preSendOutbound(Message<?> message) {
        SimpMessageHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, SimpMessageHeaderAccessor.class);
        if (accessor == null || accessor.getMessageType() != SimpMessageType.MESSAGE) return message;
        if (accessor.getSessionId() == null) return null;
        FarmScopedPrincipal principal = sessions.get(accessor.getSessionId());
        if (principal == null) return null;
        User user = authenticatedUser(principal.token());
        return user != null && principal.farmId().equals(user.getFarmId()) ? message : null;
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        sessions.remove(event.getSessionId());
    }

    private boolean isOwnedFarmDestination(StompHeaderAccessor accessor, Pattern pattern) {
        if (!(accessor.getUser() instanceof FarmScopedPrincipal principal)) {
            return false;
        }
        User user = authenticatedUser(principal.token());
        if (user == null || !principal.farmId().equals(user.getFarmId())) {
            return false;
        }
        String destination = accessor.getDestination();
        Matcher matcher = destination == null ? null : pattern.matcher(destination);
        if (matcher == null || !matcher.matches()) {
            return false;
        }
        try {
            return principal.farmId().equals(Long.valueOf(matcher.group(1)));
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private User authenticatedUser(String token) {
        try {
            JwtService.TokenIdentity identity = jwtService.identity(token);
            User user = userRepository.findById(identity.userId()).orElse(null);
            return user != null && user.getCredentialVersion() == identity.credentialVersion() ? user : null;
        } catch (io.jsonwebtoken.JwtException | IllegalArgumentException ex) {
            return null;
        }
    }

    private record FarmScopedPrincipal(String name, Long farmId, String token) implements Principal {
        @Override
        public String toString() {
            return name;
        }

        @Override
        public String getName() {
            return name;
        }
    }
}
