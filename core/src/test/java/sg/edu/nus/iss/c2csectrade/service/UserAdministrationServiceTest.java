package sg.edu.nus.iss.c2csectrade.service;

import sg.edu.nus.iss.c2csectrade.entity.User;
import sg.edu.nus.iss.c2csectrade.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static sg.edu.nus.iss.c2csectrade.TestUsers.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAdministrationServiceTest {

    @Mock
    private UserMapper userMapper;

    private UserAdministrationService service;

    private final User alice = user(2L, "alice", "ROLE_USER");
    private final User admin = user(1L, "admin", "ROLE_ADMIN");

    @BeforeEach
    void setUp() {
        service = new UserAdministrationService(userMapper);
    }

    private void given(User u) {
        when(userMapper.selectById(u.getId())).thenReturn(u);
        when(userMapper.selectByUsernameWithRoles(u.getUsername())).thenReturn(u);
    }

    private static void assertStatus(Runnable action, HttpStatus status) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(status);
    }

    @Test
    void suspendDisablesARegularUser() {
        given(alice);

        User result = service.suspend(2L);

        verify(userMapper).updateEnabled(2L, false);
        assertThat(result.getEnabled()).isFalse();
    }

    @Test
    void reinstateEnablesARegularUser() {
        alice.setEnabled(false);
        given(alice);

        User result = service.reinstate(2L);

        verify(userMapper).updateEnabled(2L, true);
        assertThat(result.getEnabled()).isTrue();
    }

    @Test
    void administratorsCannotBeSuspended() {
        given(admin);

        assertStatus(() -> service.suspend(1L), HttpStatus.FORBIDDEN);
        verify(userMapper, never()).updateEnabled(anyLong(), anyBoolean());
    }

    @Test
    void unknownUserIsNotFound() {
        when(userMapper.selectById(99L)).thenReturn(null);

        assertStatus(() -> service.suspend(99L), HttpStatus.NOT_FOUND);
        assertStatus(() -> service.reinstate(99L), HttpStatus.NOT_FOUND);
        assertStatus(() -> service.delete(99L), HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteRemovesARegularUser() {
        given(alice);

        service.delete(2L);

        verify(userMapper).deleteById(2L);
    }

    @Test
    void administratorsCannotBeDeleted() {
        given(admin);

        assertStatus(() -> service.delete(1L), HttpStatus.FORBIDDEN);
        verify(userMapper, never()).deleteById(anyLong());
    }

    @Test
    void renameTrimsAndSavesTheDisplayName() {
        given(alice);
        when(userMapper.selectAll()).thenReturn(List.of(alice, admin));

        User result = service.rename(2L, "  Alice Tan  ");

        assertThat(result.getDisplayName()).isEqualTo("Alice Tan");
        assertThat(result.getUpdatedAt()).isNotNull();
        verify(userMapper).update(alice);
    }

    @Test
    void renameRejectsBlankAndOverlongNames() {
        assertStatus(() -> service.rename(2L, "   "), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.rename(2L, null), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.rename(2L, "x".repeat(UserAdministrationService.MAX_DISPLAY_NAME_LENGTH + 1)),
                HttpStatus.BAD_REQUEST);
        verify(userMapper, never()).update(any());
    }

    @Test
    void renameRejectsANameAnotherUserHas() {
        User bob = user(3L, "bob", "ROLE_USER");
        bob.setDisplayName("Bobby");
        given(alice);
        when(userMapper.selectAll()).thenReturn(List.of(alice, bob));

        assertStatus(() -> service.rename(2L, "Bobby"), HttpStatus.CONFLICT);
        verify(userMapper, never()).update(any());
    }
}
