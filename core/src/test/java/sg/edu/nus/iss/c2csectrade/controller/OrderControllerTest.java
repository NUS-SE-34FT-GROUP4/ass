package sg.edu.nus.iss.c2csectrade.controller;

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
}
