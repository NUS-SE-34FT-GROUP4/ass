package sg.edu.nus.iss.c2csectrade.service;

import sg.edu.nus.iss.c2csectrade.entity.User;
import sg.edu.nus.iss.c2csectrade.mapper.UserMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

/**
 * Administrator actions on user accounts. Administrators cannot be suspended,
 * deleted or renamed here, which also stops an administrator locking themselves out.
 */
@Service
public class UserAdministrationService {

    static final int MAX_DISPLAY_NAME_LENGTH = 100;

    private final UserMapper userMapper;

    public UserAdministrationService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Transactional
    public User suspend(Long userId) {
        User user = regularUser(userId);
        userMapper.updateEnabled(userId, false);
        user.setEnabled(false);
        return user;
    }

    @Transactional
    public User reinstate(Long userId) {
        User user = regularUser(userId);
        userMapper.updateEnabled(userId, true);
        user.setEnabled(true);
        return user;
    }

    @Transactional
    public User rename(Long userId, String displayName) {
        String name = displayName == null ? "" : displayName.trim();
        if (name.isEmpty() || name.length() > MAX_DISPLAY_NAME_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Display name must be 1 to " + MAX_DISPLAY_NAME_LENGTH + " characters");
        }
        User user = regularUser(userId);
        boolean taken = userMapper.selectAll().stream()
                .anyMatch(u -> !u.getId().equals(userId) && name.equals(u.getDisplayName()));
        if (taken) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Display name is already in use");
        }
        user.setDisplayName(name);
        user.setUpdatedAt(Instant.now());
        userMapper.update(user);
        return user;
    }

    @Transactional
    public void delete(Long userId) {
        regularUser(userId);
        userMapper.deleteById(userId);
    }

    private User regularUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        User withRoles = userMapper.selectByUsernameWithRoles(user.getUsername());
        boolean admin = withRoles != null && withRoles.getRoles() != null
                && withRoles.getRoles().stream().anyMatch(r -> "ROLE_ADMIN".equals(r.getName()));
        if (admin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator accounts cannot be changed here");
        }
        return user;
    }
}
