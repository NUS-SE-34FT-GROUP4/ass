package lut.cn.c2cplatform.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private static String key(char fill) {
        return Base64.getEncoder().encodeToString(String.valueOf(fill).repeat(64).getBytes());
    }

    private static JwtTokenProvider provider(String secret, int expirationMs) {
        JwtTokenProvider provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "jwtSecret", secret);
        ReflectionTestUtils.setField(provider, "jwtExpirationMs", expirationMs);
        return provider;
    }

    private static String tokenFor(JwtTokenProvider provider, String username) {
        User principal = new User(username, "unused", List.of());
        return provider.generateToken(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @Test
    void aFreshTokenIsValidAndCarriesTheUsername() {
        JwtTokenProvider provider = provider(key('a'), 60_000);
        String token = tokenFor(provider, "alice");

        assertThat(provider.validateToken(token)).isTrue();
        assertThat(provider.getUsernameFromToken(token)).isEqualTo("alice");
    }

    @Test
    void aTokenSignedWithAnotherKeyIsRejected() {
        String token = tokenFor(provider(key('a'), 60_000), "alice");

        assertThat(provider(key('b'), 60_000).validateToken(token)).isFalse();
    }

    @Test
    void anExpiredTokenIsRejected() {
        JwtTokenProvider provider = provider(key('a'), -1_000);

        assertThat(provider.validateToken(tokenFor(provider, "alice"))).isFalse();
    }

    @Test
    void aTamperedOrEmptyTokenIsRejected() {
        JwtTokenProvider provider = provider(key('a'), 60_000);
        String token = tokenFor(provider, "alice");
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        assertThat(provider.validateToken(tampered)).isFalse();
        assertThat(provider.validateToken("not-a-jwt")).isFalse();
        assertThat(provider.validateToken("")).isFalse();
    }
}
