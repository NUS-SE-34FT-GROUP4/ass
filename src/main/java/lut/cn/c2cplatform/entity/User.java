package lut.cn.c2cplatform.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.Instant;
import java.util.Set;

@Data
public class User implements Serializable {
    private Long id;
    private String username;
    private String displayName;
    private String email;
    private String passwordHash;
    private String paymentPasswordHash;
    private String avatarUrl;
    private java.math.BigDecimal balance; // User balance
    private Instant createdAt;
    private Instant updatedAt;
    private Set<Role> roles;

    // Avatar URL, or the default avatar when empty
    public String getAvatarUrl() {
        if (avatarUrl == null || avatarUrl.trim().isEmpty()) {
            String nameForAvatar = (displayName != null && !displayName.trim().isEmpty()) ? displayName : username;
            return "https://ui-avatars.com/api/?name=" + nameForAvatar + "&background=007bff&color=fff&size=100";
        }
        return avatarUrl;
    }

    // Display name, or the username when empty
    public String getDisplayName() {
        return (displayName != null && !displayName.trim().isEmpty()) ? displayName : username;
    }
}
