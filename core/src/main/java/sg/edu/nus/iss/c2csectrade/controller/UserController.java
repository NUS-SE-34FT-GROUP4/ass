package sg.edu.nus.iss.c2csectrade.controller;

import sg.edu.nus.iss.c2csectrade.entity.User;
import sg.edu.nus.iss.c2csectrade.mapper.UserMapper;
import sg.edu.nus.iss.c2csectrade.service.FileStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private FileStorageService fileStorageService;

    public static class UserSummaryDTO {
        public Long id;
        public String username;
        public UserSummaryDTO(Long id, String username) {
            this.id = id;
            this.username = username;
        }
    }

    /**
     * List all users (for the chat contact list) as a slim DTO
     */
    @GetMapping
    public ResponseEntity<List<UserSummaryDTO>> getAllUsers(Authentication authentication) {
        List<User> users = userMapper.selectAll();
        List<UserSummaryDTO> dtos = users.stream()
                .map(u -> new UserSummaryDTO(u.getId(), u.getUsername()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    /**
     * Get a user by username (still returns the full record; switch to a DTO if needed)
     */
    @GetMapping("/{username}")
    public ResponseEntity<User> getUserByUsername(@PathVariable String username) {
        User user = userMapper.selectByUsername(username);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(user);
    }

    /**
     * Upload an avatar file
     */
    @PostMapping("/avatar/upload")
    public ResponseEntity<?> uploadAvatar(@RequestParam("file") MultipartFile file, Authentication authentication) {
        try {
            String username = authentication.getName();
            User user = userMapper.selectByUsername(username);

            if (user == null) {
                return ResponseEntity.status(404).body("User not found");
            }

            // Upload the file to MinIO
            String avatarUrl = fileStorageService.uploadFile(file);

            // Update the user's avatar URL
            user.setAvatarUrl(avatarUrl);
            user.setUpdatedAt(java.time.Instant.now());
            userMapper.update(user);

            Map<String, String> response = new HashMap<>();
            response.put("avatarUrl", avatarUrl);
            response.put("message", "Avatar uploaded");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Upload failed: " + e.getMessage());
        }
    }

    /**
     * Set the avatar from a URL
     */
    @PostMapping("/avatar/url")
    public ResponseEntity<?> setAvatarByUrl(@RequestBody Map<String, String> request, Authentication authentication) {
        try {
            String username = authentication.getName();
            String avatarUrl = request.get("avatarUrl");

            if (avatarUrl == null || avatarUrl.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Avatar URL must not be empty");
            }

            User user = userMapper.selectByUsername(username);
            if (user == null) {
                return ResponseEntity.status(404).body("User not found");
            }

            // Update the user's avatar URL
            user.setUpdatedAt(java.time.Instant.now());
            user.setAvatarUrl(avatarUrl);
            userMapper.update(user);

            Map<String, String> response = new HashMap<>();
            response.put("avatarUrl", avatarUrl);
            response.put("message", "Avatar updated");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Update failed: " + e.getMessage());
        }
    }

    /**
     * Reset to the default avatar
     */
    @PostMapping("/avatar/reset")
    public ResponseEntity<?> resetAvatar(Authentication authentication) {
        try {
            String username = authentication.getName();
            User user = userMapper.selectByUsername(username);

            if (user == null) {
                return ResponseEntity.status(404).body("User not found");
            }

            user.setUpdatedAt(java.time.Instant.now());
            // Clear the user's avatar URL
            user.setAvatarUrl(null);
            userMapper.update(user);

            Map<String, String> response = new HashMap<>();
            response.put("message", "Avatar reset to default");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Reset failed: " + e.getMessage());
        }
    }

    /**
     * Change the user's display name
     */
    @PutMapping("/display-name")
    public ResponseEntity<?> updateDisplayName(@RequestBody Map<String, String> request, Authentication authentication) {
        try {
            String username = authentication.getName();
            String newDisplayName = request.get("displayName");

            if (newDisplayName == null || newDisplayName.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Display name must not be empty"));
            }

            newDisplayName = newDisplayName.trim();

            // Check the length
            if (newDisplayName.length() < 2 || newDisplayName.length() > 20) {
                return ResponseEntity.badRequest().body(Map.of("message", "Display name must be 2-20 characters long"));
            }

            // Check for blocked words
            if (containsSensitiveWords(newDisplayName)) {
                return ResponseEntity.badRequest().body(Map.of("message", "Display name contains a blocked word. Please choose another"));
            }

            // Check whether another user already has it
            if (isDisplayNameExists(newDisplayName, username)) {
                return ResponseEntity.badRequest().body(Map.of("message", "This display name is already taken. Please choose another"));
            }

            User user = userMapper.selectByUsername(username);
            if (user == null) {
                return ResponseEntity.status(404).body(Map.of("message", "User not found"));
            }
            user.setUpdatedAt(java.time.Instant.now());

            // Update the display name
            user.setDisplayName(newDisplayName);
            userMapper.update(user);

            Map<String, String> response = new HashMap<>();
            response.put("message", "Display name updated");
            response.put("displayName", newDisplayName);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "Update failed: " + e.getMessage()));
        }
    }

    /**
     * Blocked words check
     */
    private boolean containsSensitiveWords(String name) {
        String[] sensitiveWords = {
            "administrator", "admin", "moderator", "system", "root", "superuser", "support",
            "official", "trash", "idiot", "fuck", "shit", "kill", "porn",
            "politics"
        };

        String lowerName = name.toLowerCase();
        for (String word : sensitiveWords) {
            if (lowerName.contains(word.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check whether a display name is already taken (excluding the current user)
     */
    private boolean isDisplayNameExists(String displayName, String currentUsername) {
        List<User> allUsers = userMapper.selectAll();
        return allUsers.stream()
                .filter(user -> !user.getUsername().equals(currentUsername))
                .anyMatch(user -> displayName.equals(user.getDisplayName()));
    }

    /**
     * Get the current user's details (including balance)
     */
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(Authentication authentication) {
        try {
            String username = authentication.getName();
            User user = userMapper.selectByUsername(username);

            if (user == null) {
                return ResponseEntity.status(404).body(Map.of("message", "User not found"));
            }

            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("id", user.getId());
            userInfo.put("username", user.getUsername());
            userInfo.put("displayName", user.getDisplayName());
            userInfo.put("email", user.getEmail());
            userInfo.put("avatarUrl", user.getAvatarUrl());
            userInfo.put("balance", user.getBalance() != null ? user.getBalance() : java.math.BigDecimal.ZERO);

            return ResponseEntity.ok(userInfo);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "Failed to load user: " + e.getMessage()));
        }
    }

    /**
     * Check whether the user has set a payment password
     */
    @GetMapping("/payment-password/check")
    public ResponseEntity<?> checkPaymentPassword(Authentication authentication) {
        try {
            String username = authentication.getName();
            User user = userMapper.selectByUsername(username);

            if (user == null) {
                return ResponseEntity.status(404).body(Map.of("message", "User not found"));
            }

            boolean hasPaymentPassword = user.getPaymentPasswordHash() != null &&
                                        !user.getPaymentPasswordHash().trim().isEmpty();

            return ResponseEntity.ok(Map.of("hasPaymentPassword", hasPaymentPassword));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "Check failed: " + e.getMessage()));
        }
    }

    /**
     * Set the payment password
     */
    @PostMapping("/payment-password/set")
    public ResponseEntity<?> setPaymentPassword(
            @RequestBody sg.edu.nus.iss.c2csectrade.dto.SetPaymentPasswordRequest request,
            Authentication authentication) {
        try {
            String username = authentication.getName();
            User user = userMapper.selectByUsername(username);

            if (user == null) {
                return ResponseEntity.status(404).body(Map.of("message", "User not found"));
            }

            // Check the password format
            if (request.getPassword() == null || !request.getPassword().matches("\\d{6}")) {
                return ResponseEntity.badRequest().body(Map.of("message", "The payment password must be 6 digits"));
            }

            // Check that both entries match
            if (!request.getPassword().equals(request.getConfirmPassword())) {
                return ResponseEntity.badRequest().body(Map.of("message", "The two passwords do not match"));
            }

            // Hash the payment password with BCrypt
            String encodedPassword = org.springframework.security.crypto.bcrypt.BCrypt.hashpw(
                request.getPassword(),
                org.springframework.security.crypto.bcrypt.BCrypt.gensalt()
            );

            user.setPaymentPasswordHash(encodedPassword);
            user.setUpdatedAt(java.time.Instant.now());
            userMapper.update(user);

            return ResponseEntity.ok(Map.of("message", "Payment password set"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "Failed to set: " + e.getMessage()));
        }
    }

    /**
     * Change the payment password
     */
    @PutMapping("/payment-password/update")
    public ResponseEntity<?> updatePaymentPassword(
            @RequestBody Map<String, String> request,
            Authentication authentication) {
        try {
            String username = authentication.getName();
            User user = userMapper.selectByUsername(username);

            if (user == null) {
                return ResponseEntity.status(404).body(Map.of("message", "User not found"));
            }

            String oldPassword = request.get("oldPassword");
            String newPassword = request.get("newPassword");
            String confirmPassword = request.get("confirmPassword");

            // Check that a payment password has been set
            if (user.getPaymentPasswordHash() == null || user.getPaymentPasswordHash().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "No payment password has been set"));
            }

            // Check the old password
            if (!org.springframework.security.crypto.bcrypt.BCrypt.checkpw(oldPassword, user.getPaymentPasswordHash())) {
                return ResponseEntity.badRequest().body(Map.of("message", "The current payment password is incorrect"));
            }

            // Check the new password format
            if (newPassword == null || !newPassword.matches("\\d{6}")) {
                return ResponseEntity.badRequest().body(Map.of("message", "The new password must be 6 digits"));
            }

            // Check that both entries match
            if (!newPassword.equals(confirmPassword)) {
                return ResponseEntity.badRequest().body(Map.of("message", "The two new passwords do not match"));
            }

            // Hash the new password
            String encodedPassword = org.springframework.security.crypto.bcrypt.BCrypt.hashpw(
                newPassword,
                org.springframework.security.crypto.bcrypt.BCrypt.gensalt()
            );

            user.setPaymentPasswordHash(encodedPassword);
            user.setUpdatedAt(java.time.Instant.now());
            userMapper.update(user);

            return ResponseEntity.ok(Map.of("message", "Payment password changed"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "Update failed: " + e.getMessage()));
        }
    }
}
