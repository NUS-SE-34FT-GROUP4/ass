package lut.cn.c2cplatform.controller;

import lut.cn.c2cplatform.entity.User;
import lut.cn.c2cplatform.mapper.UserMapper;
import lut.cn.c2cplatform.service.FileStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class AvatarController {

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private UserMapper userMapper;

    /**
     * Upload an avatar file
     */
    @PostMapping("/avatar/upload")
    public ResponseEntity<?> uploadAvatar(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        try {
            String username = authentication.getName();
            User user = userMapper.selectByUsername(username);

            if (user == null) {
                return ResponseEntity.badRequest().body("User not found");
            }

            // Upload the file to MinIO
            String avatarUrl = fileStorageService.uploadFile(file);

            // Update the user's avatar URL
            user.setAvatarUrl(avatarUrl);
            userMapper.update(user);

            Map<String, String> response = new HashMap<>();
            response.put("avatarUrl", avatarUrl);
            response.put("message", "Avatar uploaded");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Avatar upload failed: " + e.getMessage());
        }
    }

    /**
     * Set the avatar from a URL
     */
    @PostMapping("/avatar/url")
    public ResponseEntity<?> setAvatarByUrl(
            @RequestBody Map<String, String> request,
            Authentication authentication) {
        try {
            String username = authentication.getName();
            User user = userMapper.selectByUsername(username);

            if (user == null) {
                return ResponseEntity.badRequest().body("User not found");
            }

            String avatarUrl = request.get("avatarUrl");
            if (avatarUrl == null || avatarUrl.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Avatar URL must not be empty");
            }

            // Update the user's avatar URL
            user.setAvatarUrl(avatarUrl);
            userMapper.update(user);

            Map<String, String> response = new HashMap<>();
            response.put("avatarUrl", avatarUrl);
            response.put("message", "Avatar updated");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Failed to set avatar: " + e.getMessage());
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
                return ResponseEntity.badRequest().body("User not found");
            }

            // Clear the avatar URL to fall back to the default
            user.setAvatarUrl(null);
            userMapper.update(user);

            Map<String, String> response = new HashMap<>();
            response.put("message", "Avatar reset to default");
            response.put("avatarUrl", "https://ui-avatars.com/api/?name=" + username + "&background=007bff&color=fff&size=100");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Failed to reset avatar: " + e.getMessage());
        }
    }
}

