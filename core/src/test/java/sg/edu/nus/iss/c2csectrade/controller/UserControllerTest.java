package sg.edu.nus.iss.c2csectrade.controller;

import sg.edu.nus.iss.c2csectrade.dto.SetPaymentPasswordRequest;
import sg.edu.nus.iss.c2csectrade.entity.User;
import sg.edu.nus.iss.c2csectrade.mapper.UserMapper;
import sg.edu.nus.iss.c2csectrade.service.FileStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCrypt;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Profile and payment-password endpoints act on the signed-in user only.
 * A payment password is six digits and is stored as a BCrypt hash.
 * Buyer 7 is signed in as "buyer7".
 */
@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private FileStorageService fileStorageService;

    @InjectMocks
    private UserController userController;

    private final Authentication buyer7 = new UsernamePasswordAuthenticationToken("buyer7", null);
    private final Authentication ghost = new UsernamePasswordAuthenticationToken("ghost", null);

    @Test
    void theContactListHasOnlyIdsAndUsernames() {
        when(userMapper.selectAll()).thenReturn(List.of(user(7L, "buyer7"), user(9L, "seller9")));

        List<UserController.UserSummaryDTO> users = userController.getAllUsers(buyer7).getBody();

        assertThat(users).extracting(u -> u.username).containsExactly("buyer7", "seller9");
        assertThat(users).extracting(u -> u.id).containsExactly(7L, 9L);
    }

    @Test
    void aUserIsLookedUpByUsername() {
        User seller = user(9L, "seller9");
        when(userMapper.selectByUsername("seller9")).thenReturn(seller);
        when(userMapper.selectByUsername("ghost")).thenReturn(null);

        assertThat(userController.getUserByUsername("seller9").getBody()).isSameAs(seller);
        assertThat(userController.getUserByUsername("ghost").getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void anUploadedAvatarIsStoredAndSaved() throws Exception {
        User me = signedIn();
        MockMultipartFile file = new MockMultipartFile("file", "me.png", "image/png", new byte[]{1});
        when(fileStorageService.uploadFile(file)).thenReturn("https://media.example/me.png");

        ResponseEntity<?> response = userController.uploadAvatar(file, buyer7);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(me.getAvatarUrl()).isEqualTo("https://media.example/me.png");
        verify(userMapper).update(me);
    }

    @Test
    void anAvatarUploadForAnUnknownUserOrAFailedStoreIsRejected() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "me.png", "image/png", new byte[]{1});
        when(userMapper.selectByUsername("ghost")).thenReturn(null);

        assertThat(userController.uploadAvatar(file, ghost).getStatusCode().value()).isEqualTo(404);

        signedIn();
        when(fileStorageService.uploadFile(file)).thenThrow(new RuntimeException("bucket down"));
        ResponseEntity<?> failed = userController.uploadAvatar(file, buyer7);
        assertThat(failed.getStatusCode().value()).isEqualTo(500);
        assertThat(failed.getBody()).asString().contains("bucket down");
        verify(userMapper, never()).update(any());
    }

    @Test
    void anAvatarCanBeSetFromAUrl() {
        User me = signedIn();

        ResponseEntity<?> response = userController.setAvatarByUrl(Map.of("avatarUrl", "https://cdn.example/a.png"), buyer7);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(me.getAvatarUrl()).isEqualTo("https://cdn.example/a.png");
        verify(userMapper).update(me);
    }

    @Test
    void anAvatarUrlMustBeGivenAndTheUserMustExist() {
        assertThat(userController.setAvatarByUrl(Map.of("avatarUrl", " "), buyer7).getStatusCode().value()).isEqualTo(400);
        assertThat(userController.setAvatarByUrl(Map.of(), buyer7).getStatusCode().value()).isEqualTo(400);

        when(userMapper.selectByUsername("ghost")).thenReturn(null);
        assertThat(userController.setAvatarByUrl(Map.of("avatarUrl", "https://cdn.example/a.png"), ghost)
                .getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void anAvatarUrlThatCannotBeSavedIsA500() {
        User me = signedIn();
        doThrow(new RuntimeException("db down")).when(userMapper).update(me);

        assertThat(userController.setAvatarByUrl(Map.of("avatarUrl", "https://cdn.example/a.png"), buyer7)
                .getStatusCode().value()).isEqualTo(500);
    }

    @Test
    void resettingTheAvatarFallsBackToTheDefault() {
        User me = signedIn();
        me.setAvatarUrl("https://cdn.example/a.png");

        assertThat(userController.resetAvatar(buyer7).getStatusCode().value()).isEqualTo(200);
        assertThat(me.getAvatarUrl()).contains("name=buyer7");
        verify(userMapper).update(me);
    }

    @Test
    void resettingTheAvatarOfAnUnknownUserOrWithTheDatabaseDownFails() {
        when(userMapper.selectByUsername("ghost")).thenReturn(null);
        assertThat(userController.resetAvatar(ghost).getStatusCode().value()).isEqualTo(404);

        User me = signedIn();
        doThrow(new RuntimeException("db down")).when(userMapper).update(me);
        assertThat(userController.resetAvatar(buyer7).getStatusCode().value()).isEqualTo(500);
    }

    @Test
    void aValidDisplayNameIsTrimmedAndSaved() {
        User me = signedIn();
        User other = user(9L, "seller9");
        other.setDisplayName("Nine");
        when(userMapper.selectAll()).thenReturn(List.of(me, other));

        ResponseEntity<?> response = userController.updateDisplayName(Map.of("displayName", "  Seven  "), buyer7);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(me.getDisplayName()).isEqualTo("Seven");
        verify(userMapper).update(me);
    }

    @Test
    void keepingYourOwnDisplayNameIsNotADuplicate() {
        User me = signedIn();
        me.setDisplayName("Seven");
        when(userMapper.selectAll()).thenReturn(List.of(me));

        assertThat(userController.updateDisplayName(Map.of("displayName", "Seven"), buyer7)
                .getStatusCode().value()).isEqualTo(200);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "A", "ThisNameIsWayTooLongToUse"})
    void aDisplayNameMustBe2To20Characters(String name) {
        assertThat(userController.updateDisplayName(Map.of("displayName", name), buyer7)
                .getStatusCode().value()).isEqualTo(400);
        verify(userMapper, never()).update(any());
    }

    @Test
    void aMissingDisplayNameIsRejected() {
        assertThat(userController.updateDisplayName(new HashMap<>(), buyer7).getStatusCode().value()).isEqualTo(400);
    }

    @ParameterizedTest
    @ValueSource(strings = {"TheAdmin", "SYSTEM bot", "Support Desk"})
    void aDisplayNameWithABlockedWordIsRejected(String name) {
        ResponseEntity<?> response = userController.updateDisplayName(Map.of("displayName", name), buyer7);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).asString().contains("blocked word");
    }

    @Test
    void aDisplayNameTakenBySomeoneElseIsRejected() {
        User other = user(9L, "seller9");
        other.setDisplayName("Nine");
        when(userMapper.selectAll()).thenReturn(List.of(other));

        ResponseEntity<?> response = userController.updateDisplayName(Map.of("displayName", "Nine"), buyer7);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).asString().contains("already taken");
    }

    @Test
    void aDisplayNameForAnUnknownUserOrWithTheDatabaseDownFails() {
        when(userMapper.selectAll()).thenReturn(List.of());
        when(userMapper.selectByUsername("ghost")).thenReturn(null);
        assertThat(userController.updateDisplayName(Map.of("displayName", "Seven"), ghost)
                .getStatusCode().value()).isEqualTo(404);

        User me = signedIn();
        doThrow(new RuntimeException("db down")).when(userMapper).update(me);
        assertThat(userController.updateDisplayName(Map.of("displayName", "Seven"), buyer7)
                .getStatusCode().value()).isEqualTo(500);
    }

    @Test
    void theCurrentUserShowsTheirBalance() {
        User me = signedIn();
        me.setBalance(new BigDecimal("12.50"));

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) userController.getCurrentUser(buyer7).getBody();

        assertThat(body).containsEntry("id", 7L).containsEntry("balance", new BigDecimal("12.50"));
    }

    @Test
    void aUserWithNoBalanceHasZero() {
        signedIn();

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) userController.getCurrentUser(buyer7).getBody();

        assertThat(body).containsEntry("balance", BigDecimal.ZERO);
    }

    @Test
    void theCurrentUserMustExist() {
        when(userMapper.selectByUsername("ghost")).thenReturn(null);
        assertThat(userController.getCurrentUser(ghost).getStatusCode().value()).isEqualTo(404);

        when(userMapper.selectByUsername("buyer7")).thenThrow(new RuntimeException("db down"));
        assertThat(userController.getCurrentUser(buyer7).getStatusCode().value()).isEqualTo(500);
    }

    @Test
    void theCheckSaysWhetherAPaymentPasswordIsSet() {
        User me = signedIn();
        assertThat(userController.checkPaymentPassword(buyer7).getBody()).isEqualTo(Map.of("hasPaymentPassword", false));

        me.setPaymentPasswordHash("  ");
        assertThat(userController.checkPaymentPassword(buyer7).getBody()).isEqualTo(Map.of("hasPaymentPassword", false));

        me.setPaymentPasswordHash(BCrypt.hashpw("123456", BCrypt.gensalt(4)));
        assertThat(userController.checkPaymentPassword(buyer7).getBody()).isEqualTo(Map.of("hasPaymentPassword", true));
    }

    @Test
    void thePasswordCheckForAnUnknownUserOrWithTheDatabaseDownFails() {
        when(userMapper.selectByUsername("ghost")).thenReturn(null);
        assertThat(userController.checkPaymentPassword(ghost).getStatusCode().value()).isEqualTo(404);

        when(userMapper.selectByUsername("buyer7")).thenThrow(new RuntimeException("db down"));
        assertThat(userController.checkPaymentPassword(buyer7).getStatusCode().value()).isEqualTo(500);
    }

    @Test
    void aSixDigitPaymentPasswordIsStoredHashed() {
        User me = signedIn();

        ResponseEntity<?> response = userController.setPaymentPassword(passwords("123456", "123456"), buyer7);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(me.getPaymentPasswordHash()).isNotEqualTo("123456");
        assertThat(BCrypt.checkpw("123456", me.getPaymentPasswordHash())).isTrue();
        verify(userMapper).update(me);
    }

    @Test
    void aPaymentPasswordMustBeSixDigitsAndConfirmed() {
        signedIn();

        assertThat(userController.setPaymentPassword(passwords("12345", "12345"), buyer7).getStatusCode().value()).isEqualTo(400);
        assertThat(userController.setPaymentPassword(passwords("abcdef", "abcdef"), buyer7).getStatusCode().value()).isEqualTo(400);
        assertThat(userController.setPaymentPassword(passwords(null, null), buyer7).getStatusCode().value()).isEqualTo(400);
        assertThat(userController.setPaymentPassword(passwords("123456", "654321"), buyer7).getStatusCode().value()).isEqualTo(400);
        verify(userMapper, never()).update(any());
    }

    @Test
    void settingAPaymentPasswordForAnUnknownUserOrWithTheDatabaseDownFails() {
        when(userMapper.selectByUsername("ghost")).thenReturn(null);
        assertThat(userController.setPaymentPassword(passwords("123456", "123456"), ghost).getStatusCode().value()).isEqualTo(404);

        User me = signedIn();
        doThrow(new RuntimeException("db down")).when(userMapper).update(me);
        assertThat(userController.setPaymentPassword(passwords("123456", "123456"), buyer7).getStatusCode().value()).isEqualTo(500);
    }

    @Test
    void thePaymentPasswordIsChangedWithTheOldOne() {
        User me = signedIn();
        me.setPaymentPasswordHash(BCrypt.hashpw("111111", BCrypt.gensalt(4)));

        ResponseEntity<?> response = userController.updatePaymentPassword(change("111111", "222222", "222222"), buyer7);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(BCrypt.checkpw("222222", me.getPaymentPasswordHash())).isTrue();
    }

    @Test
    void changingThePaymentPasswordChecksEachStep() {
        User me = signedIn();

        assertThat(message(userController.updatePaymentPassword(change("111111", "222222", "222222"), buyer7)))
                .contains("No payment password");

        me.setPaymentPasswordHash(BCrypt.hashpw("111111", BCrypt.gensalt(4)));
        assertThat(message(userController.updatePaymentPassword(change("999999", "222222", "222222"), buyer7)))
                .contains("incorrect");
        assertThat(message(userController.updatePaymentPassword(change("111111", "22", "22"), buyer7)))
                .contains("6 digits");
        assertThat(message(userController.updatePaymentPassword(change("111111", "222222", "333333"), buyer7)))
                .contains("do not match");
        verify(userMapper, never()).update(any());
    }

    @Test
    void changingThePaymentPasswordForAnUnknownUserOrWithTheDatabaseDownFails() {
        when(userMapper.selectByUsername("ghost")).thenReturn(null);
        assertThat(userController.updatePaymentPassword(change("111111", "222222", "222222"), ghost)
                .getStatusCode().value()).isEqualTo(404);

        User me = signedIn();
        me.setPaymentPasswordHash(BCrypt.hashpw("111111", BCrypt.gensalt(4)));
        doThrow(new RuntimeException("db down")).when(userMapper).update(me);
        assertThat(userController.updatePaymentPassword(change("111111", "222222", "222222"), buyer7)
                .getStatusCode().value()).isEqualTo(500);
    }

    private User signedIn() {
        User me = user(7L, "buyer7");
        when(userMapper.selectByUsername("buyer7")).thenReturn(me);
        return me;
    }

    private static User user(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }

    private static SetPaymentPasswordRequest passwords(String password, String confirm) {
        SetPaymentPasswordRequest request = new SetPaymentPasswordRequest();
        request.setPassword(password);
        request.setConfirmPassword(confirm);
        return request;
    }

    private static Map<String, String> change(String oldPassword, String newPassword, String confirm) {
        Map<String, String> request = new HashMap<>();
        request.put("oldPassword", oldPassword);
        request.put("newPassword", newPassword);
        request.put("confirmPassword", confirm);
        return request;
    }

    private static String message(ResponseEntity<?> response) {
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        return String.valueOf(((Map<Object, Object>) response.getBody()).get("message"));
    }
}
