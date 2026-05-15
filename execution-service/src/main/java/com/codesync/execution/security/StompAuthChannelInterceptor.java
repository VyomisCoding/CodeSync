package com.codesync.execution.security;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtUtil jwtUtil;

    public StompAuthChannelInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null) {
            header = accessor.getFirstNativeHeader("authorization");
        }

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Long userId = jwtUtil.extractUserId(token);
                String role = jwtUtil.extractRole(token);

                List<SimpleGrantedAuthority> authorities = role == null || role.isBlank()
                        ? List.of()
                        : List.of(new SimpleGrantedAuthority("ROLE_" + role));

                accessor.setUser(new UsernamePasswordAuthenticationToken(
                        String.valueOf(userId),
                        token,
                        authorities
                ));
            } catch (Exception ignored) {
                accessor.setUser(null);
            }
        }

        return message;
    }
}
