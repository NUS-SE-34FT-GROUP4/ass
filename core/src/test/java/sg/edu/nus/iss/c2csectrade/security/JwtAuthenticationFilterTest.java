package sg.edu.nus.iss.c2csectrade.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider tokenProvider;
    @Mock
    private UserDetailsServiceImpl userDetailsService;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private MockFilterChain run(String authorization) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/me");
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        return chain;
    }

    @Test
    void aValidTokenAuthenticatesTheRequest() throws Exception {
        when(tokenProvider.validateToken("good")).thenReturn(true);
        when(tokenProvider.getUsernameFromToken("good")).thenReturn("alice");
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(
                User.withUsername("alice").password("x").authorities(new SimpleGrantedAuthority("ROLE_USER")).build());

        MockFilterChain chain = run("Bearer good");

        assertThat(chain.getRequest()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("alice");
    }

    @Test
    void aSuspendedUsersTokenLeavesTheRequestAnonymous() throws Exception {
        when(tokenProvider.validateToken("good")).thenReturn(true);
        when(tokenProvider.getUsernameFromToken("good")).thenReturn("alice");
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(
                User.withUsername("alice").password("x").authorities("ROLE_USER").disabled(true).build());

        MockFilterChain chain = run("Bearer good");

        assertThat(chain.getRequest()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void anInvalidOrMissingTokenLeavesTheRequestAnonymous() throws Exception {
        when(tokenProvider.validateToken("bad")).thenReturn(false);

        assertThat(run("Bearer bad").getRequest()).isNotNull();
        assertThat(run(null).getRequest()).isNotNull();
        assertThat(run("Basic abc").getRequest()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
