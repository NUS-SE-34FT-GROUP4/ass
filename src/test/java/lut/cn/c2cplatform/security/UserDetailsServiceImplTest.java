package lut.cn.c2cplatform.security;

import lut.cn.c2cplatform.entity.User;
import lut.cn.c2cplatform.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static lut.cn.c2cplatform.TestUsers.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserDetailsServiceImpl service;

    @Test
    void rolesBecomeAuthorities() {
        when(userMapper.selectByUsernameWithRoles("admin")).thenReturn(user(1L, "admin", "ROLE_ADMIN"));

        UserDetails details = service.loadUserByUsername("admin");

        assertThat(details.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_ADMIN");
        assertThat(details.getPassword()).isEqualTo("$2a$10$hash");
        assertThat(details.isEnabled()).isTrue();
    }

    @Test
    void aSuspendedUserIsDisabled() {
        User alice = user(2L, "alice", "ROLE_USER");
        alice.setEnabled(false);
        when(userMapper.selectByUsernameWithRoles("alice")).thenReturn(alice);

        assertThat(service.loadUserByUsername("alice").isEnabled()).isFalse();
    }

    @Test
    void aUserWithoutTheEnabledColumnIsEnabled() {
        User legacy = user(3L, "legacy");
        legacy.setEnabled(null);
        legacy.setRoles(null);
        when(userMapper.selectByUsernameWithRoles("legacy")).thenReturn(legacy);

        UserDetails details = service.loadUserByUsername("legacy");

        assertThat(details.isEnabled()).isTrue();
        assertThat(details.getAuthorities()).isEmpty();
    }

    @Test
    void anUnknownUserIsNotFound() {
        assertThatThrownBy(() -> service.loadUserByUsername("ghost")).isInstanceOf(UsernameNotFoundException.class);
    }
}
