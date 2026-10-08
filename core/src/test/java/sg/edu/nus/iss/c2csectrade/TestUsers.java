package sg.edu.nus.iss.c2csectrade;

import sg.edu.nus.iss.c2csectrade.entity.Role;
import sg.edu.nus.iss.c2csectrade.entity.User;

import java.util.Set;

/** Builds User and Role fixtures for unit tests. */
public final class TestUsers {

    private TestUsers() {
    }

    public static Role role(int id, String name) {
        Role role = new Role();
        role.setId(id);
        role.setName(name);
        return role;
    }

    public static User user(long id, String username, String... roleNames) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPasswordHash("$2a$10$hash");
        user.setEnabled(true);
        Set<Role> roles = new java.util.HashSet<>();
        for (int i = 0; i < roleNames.length; i++) {
            roles.add(role(i + 1, roleNames[i]));
        }
        user.setRoles(roles);
        return user;
    }
}
