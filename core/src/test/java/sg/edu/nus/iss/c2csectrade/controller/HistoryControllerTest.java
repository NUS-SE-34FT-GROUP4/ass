package sg.edu.nus.iss.c2csectrade.controller;

import sg.edu.nus.iss.c2csectrade.dto.ViewHistoryDTO;
import sg.edu.nus.iss.c2csectrade.entity.User;
import sg.edu.nus.iss.c2csectrade.security.UserDetailsImpl;
import sg.edu.nus.iss.c2csectrade.service.HistoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * A view by a guest is accepted but not tracked, and a tracking failure
 * never breaks the product page. History and interests need a signed-in user.
 */
@ExtendWith(MockitoExtension.class)
class HistoryControllerTest {

    @Mock
    private HistoryService historyService;

    @InjectMocks
    private HistoryController historyController;

    @Test
    void aSignedInViewIsTracked() {
        ResponseEntity<?> response = historyController.recordView(Map.of("productId", 42L), buyer7());

        assertThat((Map<Object, Object>) response.getBody()).containsEntry("tracked", true);
        verify(historyService).recordView(7L, 42L);
    }

    @Test
    void aGuestViewIsNotTracked() {
        Authentication notSignedIn = new UsernamePasswordAuthenticationToken("anonymousUser", null);
        Authentication otherPrincipal = new UsernamePasswordAuthenticationToken("buyer7", null, List.of());

        assertThat((Map<Object, Object>) historyController.recordView(Map.of("productId", 42L), null).getBody())
                .containsEntry("tracked", false);
        assertThat((Map<Object, Object>) historyController.recordView(Map.of("productId", 42L), notSignedIn).getBody())
                .containsEntry("tracked", false);
        assertThat((Map<Object, Object>) historyController.recordView(Map.of("productId", 42L), otherPrincipal).getBody())
                .containsEntry("tracked", false);
        verifyNoInteractions(historyService);
    }

    @Test
    void aViewNeedsAProduct() {
        assertThat(historyController.recordView(new HashMap<>(), buyer7()).getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void aTrackingFailureIsSwallowed() {
        doThrow(new RuntimeException("redis down")).when(historyService).recordView(7L, 42L);

        ResponseEntity<?> response = historyController.recordView(Map.of("productId", 42L), buyer7());

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat((Map<Object, Object>) response.getBody()).containsEntry("tracked", false);
    }

    @Test
    void historyAndInterestsAreReturnedForTheSignedInUser() {
        List<ViewHistoryDTO> history = List.of(new ViewHistoryDTO(42L, 1000L));
        when(historyService.getUserHistory(7L, 20)).thenReturn(history);
        when(historyService.getUserInterestProfile(7L, 10)).thenReturn(Map.of("Home", 2));

        assertThat(historyController.getMyHistory(20, buyer7()).getBody()).isEqualTo(history);
        assertThat(historyController.getMyInterests(10, buyer7()).getBody()).isEqualTo(Map.of("Home", 2));
    }

    @Test
    void historyAndInterestsNeedASignedInUser() {
        assertThat(historyController.getMyHistory(20, null).getStatusCode().value()).isEqualTo(401);
        assertThat(historyController.getMyInterests(10, null).getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void historyAndInterestsFailuresAre500() {
        when(historyService.getUserHistory(7L, 20)).thenThrow(new RuntimeException("redis down"));
        when(historyService.getUserInterestProfile(7L, 10)).thenThrow(new RuntimeException("redis down"));

        assertThat(historyController.getMyHistory(20, buyer7()).getStatusCode().value()).isEqualTo(500);
        assertThat(historyController.getMyInterests(10, buyer7()).getStatusCode().value()).isEqualTo(500);
    }

    private static Authentication buyer7() {
        User user = new User();
        user.setId(7L);
        user.setUsername("buyer7");
        UserDetailsImpl principal = new UserDetailsImpl(user);
        return new UsernamePasswordAuthenticationToken(principal, null, List.of());
    }
}
