package sg.edu.nus.iss.c2csectrade.controller;

import sg.edu.nus.iss.c2csectrade.dto.BuyNowRequest;
import sg.edu.nus.iss.c2csectrade.dto.PaymentRequest;

import java.util.Date;
import java.util.List;
import java.util.Map;

import sg.edu.nus.iss.c2csectrade.entity.Order;
import sg.edu.nus.iss.c2csectrade.entity.User;
import sg.edu.nus.iss.c2csectrade.mapper.UserMapper;
import sg.edu.nus.iss.c2csectrade.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * The order endpoints pass the signed-in user's id to the service, which
 * decides whether that user may act on the order.
 */
@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    @Mock
    private OrderService orderService;
    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private OrderController orderController;

    private final Authentication seller = new UsernamePasswordAuthenticationToken("sharon_seller", null);
    private final Order order = new Order();

    @BeforeEach
    void signedInAsUser9() {
        User user = new User();
        user.setId(9L);
        when(userMapper.selectByUsername("sharon_seller")).thenReturn(user);
    }

    @Test
    void confirmPassesTheCaller() {
        when(orderService.confirmOrder(1946, 9)).thenReturn(order);

        assertThat(orderController.confirmOrder(1946, seller).getBody()).isSameAs(order);
    }

    @Test
    void cancelPassesTheCaller() {
        when(orderService.cancelOrder(1947, 9)).thenReturn(order);

        assertThat(orderController.cancelOrder(1947, seller).getBody()).isSameAs(order);
    }

    @Test
    void orderDetailPassesTheCaller() {
        when(orderService.getOrderForUser(1946, 9)).thenReturn(order);

        assertThat(orderController.getOrderById(1946, seller).getBody()).isSameAs(order);
    }

    @Test
    void ordersAreCreatedForTheCaller() {
        BuyNowRequest request = new BuyNowRequest();
        request.setProductId(42L);
        request.setQuantity(2);
        when(orderService.createOrderFromCart(9)).thenReturn(order);
        when(orderService.createOrderForProduct(9, 42L, 2)).thenReturn(order);

        assertThat(orderController.createOrder(seller).getBody()).isSameAs(order);
        assertThat(orderController.buyNow(request, seller).getBody()).isSameAs(order);
    }

    @Test
    void paymentPassesTheCaller() {
        PaymentRequest payment = new PaymentRequest();
        when(orderService.payOrder(1946, 9, payment)).thenReturn(order);

        assertThat(orderController.payOrder(1946, payment, seller).getBody()).isSameAs(order);
    }

    @Test
    void theCallersPurchasesAndSalesAreListed() {
        when(orderService.getUserOrders(9)).thenReturn(List.of(order));
        when(orderService.getSellerOrders(9)).thenReturn(List.of());

        assertThat(orderController.getUserOrders(seller).getBody()).containsExactly(order);
        assertThat(orderController.getSellerOrders(seller).getBody()).isEmpty();
    }

    @Test
    void anOpenPendingOrderIsReportedButNotAnExpiredOne() {
        Order paid = new Order();
        paid.setStatus("PAID");
        when(orderService.getUserOrders(9)).thenReturn(List.of(paid, pending(1, -60_000), pending(2, 60_000)));

        Map<?, ?> body = (Map<?, ?>) orderController.checkPendingOrders(seller).getBody();

        assertThat(body.get("hasPendingOrder")).isEqualTo(true);
        assertThat(body.get("orderId")).isEqualTo(2);
    }

    @Test
    void onlyExpiredOrdersMeanNothingIsPending() {
        when(orderService.getUserOrders(9)).thenReturn(List.of(pending(1, -60_000)));

        Map<?, ?> body = (Map<?, ?>) orderController.checkPendingOrders(seller).getBody();

        assertThat(body.get("hasPendingOrder")).isEqualTo(false);
        assertThat(body.containsKey("orderId")).isFalse();
    }

    private static Order pending(int id, long expiresInMillis) {
        Order pending = new Order();
        pending.setId(id);
        pending.setStatus("PENDING");
        pending.setExpireTime(new Date(System.currentTimeMillis() + expiresInMillis));
        return pending;
    }
}
