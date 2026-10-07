package lut.cn.c2cplatform.service;

import lut.cn.c2cplatform.entity.User;
import lut.cn.c2cplatform.exception.UserAlreadyExistsException;
import lut.cn.c2cplatform.mapper.RoleMapper;
import lut.cn.c2cplatform.mapper.UserMapper;
import lut.cn.c2cplatform.mapper.UserRoleMapper;
import lut.cn.c2cplatform.payload.PasswordResetRequest;
import lut.cn.c2cplatform.payload.RegisterRequest;
import lut.cn.c2cplatform.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static lut.cn.c2cplatform.TestUsers.role;
import static lut.cn.c2cplatform.TestUsers.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private RoleMapper roleMapper;
    @Mock
    private UserRoleMapper userRoleMapper;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(userMapper, roleMapper, userRoleMapper, passwordEncoder);
    }

    private static RegisterRequest registration(String username, String email) {
        RegisterRequest request = new RegisterRequest();
        request.setUsername(username);
        request.setDisplayName("Display " + username);
        request.setEmail(email);
        request.setPassword("secret123");
        return request;
    }

    @Test
    void registerHashesThePasswordAndGrantsRoleUser() {
        when(userMapper.selectAll()).thenReturn(List.of());
        when(roleMapper.selectByName("ROLE_USER")).thenReturn(role(2, "ROLE_USER"));
        doAnswer(inv -> {
            inv.<User>getArgument(0).setId(42L);
            return 1;
        }).when(userMapper).insert(any(User.class));
        User saved = user(42L, "carol", "ROLE_USER");
        when(userMapper.selectByUsernameWithRoles("carol")).thenReturn(saved);

        User result = authService.registerUser(registration("carol", "carol@example.com"));

        ArgumentCaptor<User> inserted = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(inserted.capture());
        assertThat(inserted.getValue().getPasswordHash()).isNotEqualTo("secret123");
        assertThat(passwordEncoder.matches("secret123", inserted.getValue().getPasswordHash())).isTrue();
        verify(userRoleMapper).insertUserRole(42L, 2);
        assertThat(result).isSameAs(saved);
    }

    @Test
    void registerRejectsTheReservedAdminName() {
        assertThatThrownBy(() -> authService.registerUser(registration("Admin", "x@example.com")))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("reserved");
        verify(userMapper, never()).insert(any());
    }

    @Test
    void registerRejectsATakenUsername() {
        when(userMapper.selectByUsername("alice")).thenReturn(user(2L, "alice"));

        assertThatThrownBy(() -> authService.registerUser(registration("alice", "new@example.com")))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("Username");
    }

    @Test
    void registerRejectsATakenEmail() {
        when(userMapper.selectAll()).thenReturn(List.of(user(2L, "alice")));

        assertThatThrownBy(() -> authService.registerUser(registration("dave", "alice@example.com")))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("Email");
    }

    @Test
    void registerFailsWhenRoleUserIsMissing() {
        when(userMapper.selectAll()).thenReturn(List.of());
        when(roleMapper.selectByName("ROLE_USER")).thenReturn(null);

        assertThatThrownBy(() -> authService.registerUser(registration("erin", "erin@example.com")))
                .isInstanceOf(RuntimeException.class);
        verify(userMapper, never()).insert(any());
    }

    private static PasswordResetRequest reset(String username, String email) {
        PasswordResetRequest request = new PasswordResetRequest();
        request.setUsername(username);
        request.setEmail(email);
        request.setNewPassword("newSecret1");
        return request;
    }

    @Test
    void resetPasswordNeedsAMatchingEmail() {
        when(userMapper.selectByUsername("alice")).thenReturn(user(2L, "alice"));

        assertThat(authService.resetPassword(reset("alice", "wrong@example.com"))).isFalse();
        verify(userMapper, never()).update(any());
    }

    @Test
    void resetPasswordFailsForAnUnknownUser() {
        assertThat(authService.resetPassword(reset("nobody", "nobody@example.com"))).isFalse();
    }

    @Test
    void resetPasswordStoresANewHash() {
        User alice = user(2L, "alice");
        when(userMapper.selectByUsername("alice")).thenReturn(alice);

        assertThat(authService.resetPassword(reset("alice", "alice@example.com"))).isTrue();

        verify(userMapper).update(alice);
        assertThat(passwordEncoder.matches("newSecret1", alice.getPasswordHash())).isTrue();
    }
}
