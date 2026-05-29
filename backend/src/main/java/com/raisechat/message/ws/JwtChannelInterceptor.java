package com.raisechat.message.ws;

import com.raisechat.auth.jwt.AuthenticatedUser;
import com.raisechat.auth.jwt.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
public class JwtChannelInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;

    public JwtChannelInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                throw new MessagingException("Authorization header missing or invalid");
            }
            String token = authHeader.substring(7);
            try {
                Jws<Claims> jws = jwtService.parseAccessToken(token);
                Claims claims = jws.getPayload();
                Long id = Long.valueOf(claims.getSubject());
                String userId = claims.get("userId", String.class);
                AuthenticatedUser principal = new AuthenticatedUser(id, userId);
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
                accessor.setUser(auth);
            } catch (Exception e) {
                throw new MessagingException("JWT validation failed: " + e.getMessage());
            }
        }
        return message;
    }
}
