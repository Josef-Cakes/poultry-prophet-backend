package com.poultryprophet.realtime;

import com.poultryprophet.security.JwtService;
import com.poultryprophet.user.User;
import com.poultryprophet.user.UserRepository;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
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
            if (!jwtService.isValid(token)) {
                return null;
            }
            String email = jwtService.extractEmail(token);
            User user = userRepository.findByEmail(email).orElse(null);
            if (user == null || user.getFarmId() == null) {
                return null;
            }
            accessor.setUser(new FarmScopedPrincipal(email, user.getFarmId()));
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

    private boolean isOwnedFarmDestination(StompHeaderAccessor accessor, Pattern pattern) {
        if (!(accessor.getUser() instanceof FarmScopedPrincipal principal)) {
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

    private record FarmScopedPrincipal(String name, Long farmId) implements Principal {
        @Override
        public String getName() {
            return name;
        }
    }
}
