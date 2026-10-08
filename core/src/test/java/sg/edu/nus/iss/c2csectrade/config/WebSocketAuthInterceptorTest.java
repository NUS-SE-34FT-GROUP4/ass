package sg.edu.nus.iss.c2csectrade.config;

import sg.edu.nus.iss.c2csectrade.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketAuthInterceptorTest {

    @Mock
    private JwtTokenProvider tokenProvider;
    @Mock
    private UserDetailsService userDetailsService;

    @InjectMocks
    private WebSocketAuthInterceptor interceptor;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static Message<byte[]> connect(String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (authorization != null) {
            accessor.addNativeHeader("Authorization", authorization);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void aValidTokenSetsTheSessionUser() {
        when(tokenProvider.validateToken("good")).thenReturn(true);
        when(tokenProvider.getUsernameFromToken("good")).thenReturn("alice");
        when(userDetailsService.loadUserByUsername("alice"))
                .thenReturn(User.withUsername("alice").password("x").authorities("ROLE_USER").build());

        Message<?> result = interceptor.preSend(connect("Bearer good"), null);

        assertThat(StompHeaderAccessor.wrap(result).getUser().getName()).isEqualTo("alice");
    }

    @Test
    void connectWithoutATokenIsRefused() {
        assertThatThrownBy(() -> interceptor.preSend(connect(null), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anInvalidTokenIsRefused() {
        when(tokenProvider.validateToken("bad")).thenReturn(false);

        assertThatThrownBy(() -> interceptor.preSend(connect("Bearer bad"), null))
                .hasMessageContaining("invalid token");
    }

    @Test
    void aSuspendedUserIsRefused() {
        when(tokenProvider.validateToken("good")).thenReturn(true);
        when(tokenProvider.getUsernameFromToken("good")).thenReturn("alice");
        when(userDetailsService.loadUserByUsername("alice"))
                .thenReturn(User.withUsername("alice").password("x").authorities("ROLE_USER").disabled(true).build());

        assertThatThrownBy(() -> interceptor.preSend(connect("Bearer good"), null))
                .hasMessageContaining("suspended");
    }

    @Test
    void otherFramesPassThrough() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        Message<byte[]> send = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThat(interceptor.preSend(send, null)).isSameAs(send);
    }
}
