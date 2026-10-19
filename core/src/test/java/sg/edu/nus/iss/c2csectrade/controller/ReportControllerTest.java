package sg.edu.nus.iss.c2csectrade.controller;

import sg.edu.nus.iss.c2csectrade.entity.Report;
import sg.edu.nus.iss.c2csectrade.entity.User;
import sg.edu.nus.iss.c2csectrade.security.UserDetailsImpl;
import sg.edu.nus.iss.c2csectrade.service.ProductService;
import sg.edu.nus.iss.c2csectrade.service.ReportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * A report is always filed under the signed-in user, whatever form the
 * principal takes; the reporter id in the request body is ignored.
 */
@ExtendWith(MockitoExtension.class)
class ReportControllerTest {

    @Mock
    private ReportService reportService;
    @Mock
    private ProductService productService;

    @InjectMocks
    private ReportController reportController;

    @Test
    void aReportIsFiledUnderTheSignedInUser() {
        Report report = new Report();
        report.setReporterId(99L);

        ResponseEntity<?> response = reportController.createReport(report, signedIn(new UserDetailsImpl(buyer5())));

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(report.getReporterId()).isEqualTo(5L);
        verify(reportService).createReport(report);
    }

    @Test
    void aUsernamePrincipalIsLookedUp() {
        when(productService.getUserByUsername("buyer5")).thenReturn(buyer5());
        Report report = new Report();

        assertThat(reportController.createReport(report, signedIn("buyer5")).getStatusCode().value()).isEqualTo(201);
        assertThat(report.getReporterId()).isEqualTo(5L);
    }

    @Test
    void aSpringUserPrincipalIsLookedUp() {
        when(productService.getUserByUsername("buyer5")).thenReturn(buyer5());
        var principal = new org.springframework.security.core.userdetails.User("buyer5", "unused", List.of());
        Report report = new Report();

        assertThat(reportController.createReport(report, signedIn(principal)).getStatusCode().value()).isEqualTo(201);
        assertThat(report.getReporterId()).isEqualTo(5L);
    }

    @Test
    void aUserWhoNoLongerExistsCannotReport() {
        when(productService.getUserByUsername("buyer5")).thenReturn(null);

        assertThat(reportController.createReport(new Report(), signedIn("buyer5")).getStatusCode().value()).isEqualTo(401);
        verifyNoInteractions(reportService);
    }

    @Test
    void anUnrecognisedPrincipalCannotReport() {
        assertThat(reportController.createReport(new Report(), signedIn(42)).getStatusCode().value()).isEqualTo(401);
        verifyNoInteractions(reportService);
    }

    @Test
    void aDuplicateReportIsABadRequest() {
        doThrow(new RuntimeException("You have already reported this product"))
                .when(reportService).createReport(any());

        ResponseEntity<?> response = reportController.createReport(new Report(), signedIn(new UserDetailsImpl(buyer5())));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).asString().contains("already reported");
    }

    private static Authentication signedIn(Object principal) {
        return new UsernamePasswordAuthenticationToken(principal, null);
    }

    private static User buyer5() {
        User user = new User();
        user.setId(5L);
        user.setUsername("buyer5");
        return user;
    }
}
