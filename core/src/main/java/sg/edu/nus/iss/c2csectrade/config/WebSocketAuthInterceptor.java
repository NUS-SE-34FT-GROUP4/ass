package sg.edu.nus.iss.c2csectrade.config;

import sg.edu.nus.iss.c2csectrade.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private UserDetailsService userDetailsService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                throw new IllegalArgumentException("Unauthorized WebSocket CONNECT: missing Bearer token");
            }

            // Same key and checks as JwtAuthenticationFilter for HTTP requests.
            String token = authHeader.substring(7);
            if (!tokenProvider.validateToken(token)) {
                throw new IllegalArgumentException("Unauthorized WebSocket CONNECT: invalid token");
            }
            UserDetails user = userDetailsService.loadUserByUsername(tokenProvider.getUsernameFromToken(token));
            if (!user.isEnabled()) {
                throw new IllegalArgumentException("Unauthorized WebSocket CONNECT: account suspended");
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(user.getUsername(), null, user.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            accessor.setUser(authentication);

            // Make sure the Principal is stored in the WebSocket session
            accessor.setLeaveMutable(true);
        }

        return message;
    }
}
