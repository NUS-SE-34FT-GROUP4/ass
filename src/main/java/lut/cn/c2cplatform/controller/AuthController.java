package lut.cn.c2cplatform.controller;

import lut.cn.c2cplatform.payload.RegisterRequest;
import lut.cn.c2cplatform.payload.LoginRequest;
import lut.cn.c2cplatform.payload.PasswordResetRequest;
import lut.cn.c2cplatform.payload.AuthResponse;
import lut.cn.c2cplatform.service.AuthService;
import lut.cn.c2cplatform.service.CaptchaService;
import lut.cn.c2cplatform.security.JwtTokenProvider;
import lut.cn.c2cplatform.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    @Autowired
    private AuthService authService;
    @Autowired
    private AuthenticationManager authenticationManager;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private CaptchaService captchaService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest registerRequest) {
        if (!captchaService.verifyAndConsume(registerRequest.getCaptchaId(), registerRequest.getCaptchaCode())) {
            return ResponseEntity.badRequest().body("Captcha is incorrect or has expired");
        }

        User user = authService.registerUser(registerRequest);
        return ResponseEntity.ok(user);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        if (!captchaService.verifyAndConsume(loginRequest.getCaptchaId(), loginRequest.getCaptchaCode())) {
            return ResponseEntity.badRequest().body("Captcha is incorrect or has expired");
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword())
            );
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Incorrect username or password");
        }
        SecurityContextHolder.getContext().setAuthentication(authentication);

        boolean isAdmin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_ADMIN"));
        boolean wantAdmin = Boolean.TRUE.equals(loginRequest.getIsAdmin());
        if (wantAdmin && !isAdmin) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Administrator access required. Untick the option or sign in with an administrator account.");
        }

        String token = jwtTokenProvider.generateToken(authentication);
        String role = authentication.getAuthorities().stream().findFirst().map(Object::toString).orElse("");
        // Load the full user record
        User user = authService.getUserByUsername(loginRequest.getUsername());

        return ResponseEntity.ok(new AuthResponse(
            token,
            user.getUsername(),
            role,
            user.getId(),
            user.getDisplayName(),
            user.getAvatarUrl(),
            user.getEmail()
        ));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody PasswordResetRequest request) {
        // Check the captcha
        if (!captchaService.verifyAndConsume(request.getCaptchaId(), request.getCaptchaCode())) {
            return ResponseEntity.badRequest().body("Captcha is incorrect or has expired");
        }

        // Check the new password length
        if (request.getNewPassword() == null || request.getNewPassword().length() < 6) {
            return ResponseEntity.badRequest().body("The new password must be at least 6 characters");
        }

        // Reset the password
        boolean success = authService.resetPassword(request);

        if (success) {
            return ResponseEntity.ok("Password reset. Please sign in with your new password");
        } else {
            return ResponseEntity.badRequest().body("Username and email do not match. Please check and try again");
        }
    }
}
