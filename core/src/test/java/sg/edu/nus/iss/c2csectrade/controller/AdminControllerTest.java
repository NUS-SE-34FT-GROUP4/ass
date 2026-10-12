package sg.edu.nus.iss.c2csectrade.controller;

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
}
