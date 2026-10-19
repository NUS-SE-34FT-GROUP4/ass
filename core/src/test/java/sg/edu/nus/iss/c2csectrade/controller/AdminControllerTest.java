package sg.edu.nus.iss.c2csectrade.controller;

import sg.edu.nus.iss.c2csectrade.dto.ReportDTO;
import sg.edu.nus.iss.c2csectrade.entity.Report;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.verifyNoInteractions;

import sg.edu.nus.iss.c2csectrade.entity.Product;
import sg.edu.nus.iss.c2csectrade.entity.Report;
import sg.edu.nus.iss.c2csectrade.entity.User;
import sg.edu.nus.iss.c2csectrade.event.EventPublisher;
import sg.edu.nus.iss.c2csectrade.event.Events;
import sg.edu.nus.iss.c2csectrade.mapper.UserMapper;
import sg.edu.nus.iss.c2csectrade.service.ProductService;
import sg.edu.nus.iss.c2csectrade.service.ReportService;
import sg.edu.nus.iss.c2csectrade.service.UserAdministrationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private UserAdministrationService userAdministrationService;
    @Mock
    private ReportService reportService;
    @Mock
    private ProductService productService;
    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private AdminController adminController;

    @Test
    void delistProductPublishesEventToNotifyTheSeller() {
        long productId = 42L;
        long reportId = 1L;
        Product product = new Product();
        product.setId(productId);
        product.setUserId(7L);
        product.setName("Desk lamp");

        User owner = new User();
        owner.setId(7L);
        owner.setUsername("seller1");

        when(productService.getProductById(productId)).thenReturn(product);
        when(userMapper.selectById(7L)).thenReturn(owner);

        ResponseEntity<?> response = adminController.delistProduct(productId, reportId);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(productService).delistProduct(productId);

        ArgumentCaptor<Events.ProductDelistedEvent> eventCaptor = ArgumentCaptor.forClass(Events.ProductDelistedEvent.class);
        verify(eventPublisher).publishProductDelisted(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getProductId()).isEqualTo(productId);
        assertThat(eventCaptor.getValue().getProductName()).isEqualTo("Desk lamp");
        assertThat(eventCaptor.getValue().getSellerUsername()).isEqualTo("seller1");

        verify(reportService).updateReportStatus(reportId, "APPROVED");
    }

    @Test
    void delistProductReturns404WhenProductNotFound() {
        when(productService.getProductById(99L)).thenReturn(null);

        ResponseEntity<?> response = adminController.delistProduct(99L, 1L);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        verify(productService, never()).delistProduct(anyLong());
        verify(eventPublisher, never()).publishProductDelisted(any());
    }

    @Test
    void delistProductStillSucceedsWhenEventPublishFails() {
        long productId = 42L;
        Product product = new Product();
        product.setId(productId);
        product.setUserId(7L);
        product.setName("Desk lamp");
        User owner = new User();
        owner.setId(7L);
        owner.setUsername("seller1");

        when(productService.getProductById(productId)).thenReturn(product);
        when(userMapper.selectById(7L)).thenReturn(owner);
        doThrow(new RuntimeException("broker down"))
                .when(eventPublisher).publishProductDelisted(any());

        ResponseEntity<?> response = adminController.delistProduct(productId, 1L);

        // The delist itself succeeds; only the notification is lost.
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(productService).delistProduct(productId);
    }

    @Test
    void usersAreListedWithTheirRoles() {
        List<User> users = List.of(new User());
        when(userMapper.selectAllWithRoles()).thenReturn(users);

        assertThat(adminController.getAllUsers().getBody()).isSameAs(users);
    }

    @Test
    void userAdministrationIsLeftToTheService() {
        User user = new User();
        when(userAdministrationService.suspend(7L)).thenReturn(user);
        when(userAdministrationService.reinstate(7L)).thenReturn(user);
        when(userAdministrationService.rename(7L, "Lamp Seller")).thenReturn(user);

        assertThat(adminController.deleteUser(7L).getStatusCode().value()).isEqualTo(200);
        verify(userAdministrationService).delete(7L);
        assertThat(adminController.suspendUser(7L).getBody()).isSameAs(user);
        assertThat(adminController.reinstateUser(7L).getBody()).isSameAs(user);
        assertThat(adminController.renameUser(7L, Map.of("displayName", "Lamp Seller")).getBody()).isSameAs(user);
    }

    @Test
    void reportsShowTheProductAndReporterEvenWhenEitherIsGone() {
        when(reportService.getAllReports()).thenReturn(List.of(report(1L, 42L, 5L), report(2L, 43L, 6L)));
        when(productService.getProductById(42L)).thenReturn(lampOfSeller7());
        when(productService.getProductById(43L)).thenReturn(null);
        User reporter = new User();
        reporter.setUsername("buyer5");
        reporter.setDisplayName("Buyer Five");
        when(userMapper.selectById(5L)).thenReturn(reporter);
        when(userMapper.selectById(6L)).thenReturn(null);

        List<ReportDTO> reports = adminController.getAllReports().getBody();

        assertThat(reports.get(0).getProductName()).isEqualTo("Desk lamp");
        assertThat(reports.get(0).getReporterDisplayName()).isEqualTo("Buyer Five");
        assertThat(reports.get(1).getProductName()).isEqualTo("Unknown product");
        assertThat(reports.get(1).getReporterUsername()).isEqualTo("Unknown user");
        assertThat(reports.get(1).getReporterDisplayName()).isEqualTo("Unknown user");
    }

    @Test
    void anAdminCanRejectAReport() {
        Report body = new Report();
        body.setStatus("REJECTED");

        assertThat(adminController.updateReportStatus(1L, body).getStatusCode().value()).isEqualTo(200);
        verify(reportService).updateReportStatus(1L, "REJECTED");
    }

    @Test
    void aReportStatusThatCannotBeSavedIsA500() {
        Report body = new Report();
        body.setStatus("REJECTED");
        doThrow(new RuntimeException("db down")).when(reportService).updateReportStatus(1L, "REJECTED");

        assertThat(adminController.updateReportStatus(1L, body).getStatusCode().value()).isEqualTo(500);
    }

    @Test
    void delistingAProductWhoseSellerIsGoneSendsNoNotice() {
        when(productService.getProductById(42L)).thenReturn(lampOfSeller7());
        when(userMapper.selectById(7L)).thenReturn(null);

        assertThat(adminController.delistProduct(42L, 1L).getStatusCode().value()).isEqualTo(200);
        verify(eventPublisher, never()).publishProductDelisted(any());
        verify(reportService).updateReportStatus(1L, "APPROVED");
    }

    @Test
    void aReportThatCannotBeClosedDoesNotUndoTheDelisting() {
        User owner = new User();
        owner.setUsername("seller1");
        when(productService.getProductById(42L)).thenReturn(lampOfSeller7());
        when(userMapper.selectById(7L)).thenReturn(owner);
        doThrow(new RuntimeException("db down")).when(reportService).updateReportStatus(1L, "APPROVED");

        assertThat(adminController.delistProduct(42L, 1L).getStatusCode().value()).isEqualTo(200);
        verify(productService).delistProduct(42L);
    }

    @Test
    void aDelistingThatFailsIsA500AndNotifiesNobody() {
        when(productService.getProductById(42L)).thenReturn(lampOfSeller7());
        doThrow(new RuntimeException("db down")).when(productService).delistProduct(42L);

        assertThat(adminController.delistProduct(42L, 1L).getStatusCode().value()).isEqualTo(500);
        verifyNoInteractions(eventPublisher);
    }

    private static Product lampOfSeller7() {
        Product product = new Product();
        product.setId(42L);
        product.setUserId(7L);
        product.setName("Desk lamp");
        return product;
    }

    private static Report report(Long id, Long productId, Long reporterId) {
        Report report = new Report();
        report.setId(id);
        report.setProductId(productId);
        report.setReporterId(reporterId);
        report.setStatus("PENDING");
        return report;
    }
}
