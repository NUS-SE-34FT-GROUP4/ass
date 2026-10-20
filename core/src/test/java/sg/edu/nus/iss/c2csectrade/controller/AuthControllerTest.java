package sg.edu.nus.iss.c2csectrade.controller;

import sg.edu.nus.iss.c2csectrade.entity.User;
import sg.edu.nus.iss.c2csectrade.payload.AuthResponse;
import sg.edu.nus.iss.c2csectrade.payload.LoginRequest;
import sg.edu.nus.iss.c2csectrade.payload.PasswordResetRequest;
import sg.edu.nus.iss.c2csectrade.payload.RegisterRequest;
import sg.edu.nus.iss.c2csectrade.security.JwtTokenProvider;
import sg.edu.nus.iss.c2csectrade.service.AuthService;
import sg.edu.nus.iss.c2csectrade.service.CaptchaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Register, sign-in and password reset each consume a captcha first.
 * A suspended account is a 403, wrong credentials a 401, and ticking
 * "administrator" with a normal account is refused.
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private CaptchaService captchaService;

    @InjectMocks
    private AuthController authController;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void registeringNeedsAValidCaptcha() {
        RegisterRequest request = new RegisterRequest();
        request.setCaptchaId("c1");
        request.setCaptchaCode("ab12");
        User user = new User();
        when(captchaService.verifyAndConsume("c1", "ab12")).thenReturn(true).thenReturn(false);
        when(authService.registerUser(request)).thenReturn(user);

        assertThat(authController.register(request).getBody()).isSameAs(user);
        assertThat(authController.register(request).getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void aSignInReturnsATokenAndProfile() {
        captchaPasses();
        Authentication signedIn = authenticated("ROLE_USER");
        when(authenticationManager.authenticate(any())).thenReturn(signedIn);
        when(jwtTokenProvider.generateToken(signedIn)).thenReturn("jwt");
        when(authService.getUserByUsername("buyer7")).thenReturn(buyer7());

        ResponseEntity<?> response = authController.login(login(false));

        AuthResponse body = (AuthResponse) response.getBody();
        assertThat(body.getToken()).isEqualTo("jwt");
        assertThat(body.getRole()).isEqualTo("ROLE_USER");
        assertThat(body.getUserId()).isEqualTo(7L);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(signedIn);
    }

    @Test
    void anAdminCanSignInAsAdmin() {
        captchaPasses();
        Authentication admin = authenticated("ROLE_ADMIN");
        when(authenticationManager.authenticate(any())).thenReturn(admin);
        when(jwtTokenProvider.generateToken(admin)).thenReturn("jwt");
        when(authService.getUserByUsername("buyer7")).thenReturn(buyer7());

        assertThat(authController.login(login(true)).getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void aNormalAccountCannotSignInAsAdmin() {
        captchaPasses();
        when(authenticationManager.authenticate(any())).thenReturn(authenticated("ROLE_USER"));

        assertThat(authController.login(login(true)).getStatusCode().value()).isEqualTo(403);
        verifyNoInteractions(jwtTokenProvider);
    }

    @Test
    void aSuspendedAccountIs403AndWrongCredentials401() {
        captchaPasses();
        when(authenticationManager.authenticate(any()))
                .thenThrow(new DisabledException("disabled"))
                .thenThrow(new BadCredentialsException("bad"));

        assertThat(authController.login(login(false)).getStatusCode().value()).isEqualTo(403);
        assertThat(authController.login(login(false)).getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void aSignInWithAWrongCaptchaIsNotAttempted() {
        when(captchaService.verifyAndConsume(any(), any())).thenReturn(false);

        assertThat(authController.login(login(false)).getStatusCode().value()).isEqualTo(400);
        verifyNoInteractions(authenticationManager);
    }

    @Test
    void aPasswordIsResetWhenUsernameAndEmailMatch() {
        captchaPasses();
        PasswordResetRequest request = reset("newpass1");
        when(authService.resetPassword(request)).thenReturn(true).thenReturn(false);

        assertThat(authController.resetPassword(request).getStatusCode().value()).isEqualTo(200);
        assertThat(authController.resetPassword(request).getBody()).asString().contains("do not match");
    }

    @Test
    void aResetNeedsACaptchaAndASixCharacterPassword() {
        when(captchaService.verifyAndConsume(any(), any())).thenReturn(false).thenReturn(true);

        assertThat(authController.resetPassword(reset("newpass1")).getBody()).asString().contains("Captcha");
        assertThat(authController.resetPassword(reset("short")).getBody()).asString().contains("at least 6");
        verifyNoInteractions(authService);
    }

    @Test
    void aResetWithoutANewPasswordIsRejected() {
        captchaPasses();

        assertThat(authController.resetPassword(reset(null)).getStatusCode().value()).isEqualTo(400);
    }

    private void captchaPasses() {
        when(captchaService.verifyAndConsume(any(), any())).thenReturn(true);
    }

    private static Authentication authenticated(String role) {
        return new UsernamePasswordAuthenticationToken("buyer7", null, List.of(new SimpleGrantedAuthority(role)));
    }

    private static LoginRequest login(boolean asAdmin) {
        LoginRequest request = new LoginRequest();
        request.setUsername("buyer7");
        request.setPassword("secret");
        request.setIsAdmin(asAdmin);
        return request;
    }

    private static PasswordResetRequest reset(String newPassword) {
        PasswordResetRequest request = new PasswordResetRequest();
        request.setUsername("buyer7");
        request.setEmail("b7@example.com");
        request.setNewPassword(newPassword);
        return request;
    }

    private static User buyer7() {
        User user = new User();
        user.setId(7L);
        user.setUsername("buyer7");
        return user;
    }
}
