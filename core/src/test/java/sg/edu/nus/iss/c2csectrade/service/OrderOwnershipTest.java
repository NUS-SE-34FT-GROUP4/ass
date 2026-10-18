package sg.edu.nus.iss.c2csectrade.service;

import sg.edu.nus.iss.c2csectrade.entity.Order;
import sg.edu.nus.iss.c2csectrade.entity.OrderItem;
import sg.edu.nus.iss.c2csectrade.entity.Product;
import sg.edu.nus.iss.c2csectrade.mapper.BargainActivityMapper;
import sg.edu.nus.iss.c2csectrade.mapper.CartItemMapper;
import sg.edu.nus.iss.c2csectrade.mapper.OrderItemMapper;
import sg.edu.nus.iss.c2csectrade.mapper.OrderMapper;
import sg.edu.nus.iss.c2csectrade.mapper.ProductMapper;
import sg.edu.nus.iss.c2csectrade.mapper.TransactionMapper;
import sg.edu.nus.iss.c2csectrade.mapper.UserMapper;
import sg.edu.nus.iss.c2csectrade.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Only the buyer may confirm receipt (which releases escrow to the seller) or
 * cancel an order; an order is visible only to its buyer and its sellers.
 * Buyer is user 7, the seller of product 42 is user 9, user 5 is a stranger.
 */
@ExtendWith(MockitoExtension.class)
class OrderOwnershipTest {

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private OrderItemMapper orderItemMapper;
    @Mock
    private TransactionMapper transactionMapper;
    @Mock
    private CartItemMapper cartItemMapper;
    @Mock
    private ProductMapper productMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private BargainActivityMapper bargainActivityMapper;
    @Mock
    private CreditScoreService creditScoreService;

    @InjectMocks
    private OrderServiceImpl orderService;

    @Test
    void theSellerCannotConfirmReceiptAndPayThemselves() {
        when(orderMapper.findById(1)).thenReturn(order("PAID"));

        assertThatThrownBy(() -> orderService.confirmOrder(1, 9))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(userMapper, creditScoreService);
        verify(orderMapper, never()).updateStatus(any());
    }

    @Test
    void anotherUserCannotCancelTheOrder() {
        when(orderMapper.findById(1)).thenReturn(order("PENDING"));

        assertThatThrownBy(() -> orderService.cancelOrder(1, 5))
                .isInstanceOf(AccessDeniedException.class);
        verify(orderMapper, never()).updateStatusIfPending(any());
    }

    @Test
    void theBuyerCanCancelTheirOrder() {
        when(orderMapper.findById(1)).thenReturn(order("PENDING"));
        when(orderMapper.updateStatusIfPending(any())).thenReturn(1);

        assertThat(orderService.cancelOrder(1, 7).getStatus()).isEqualTo("CANCELED");
    }

    @Test
    void theBuyerCanSeeTheOrder() {
        when(orderMapper.findById(1)).thenReturn(order("PAID"));

        assertThat(orderService.getOrderForUser(1, 7).getId()).isEqualTo(1);
        verifyNoInteractions(orderItemMapper);
    }

    @Test
    void theSellerOfAnItemCanSeeTheOrder() {
        when(orderMapper.findById(1)).thenReturn(order("PAID"));
        when(orderItemMapper.findByOrderId(1)).thenReturn(List.of(item()));
        when(productMapper.selectById(42L)).thenReturn(productSoldBy(9L));

        assertThat(orderService.getOrderForUser(1, 9).getId()).isEqualTo(1);
    }

    @Test
    void aStrangerCannotSeeTheOrder() {
        when(orderMapper.findById(1)).thenReturn(order("PAID"));
        when(orderItemMapper.findByOrderId(1)).thenReturn(List.of(item()));
        when(productMapper.selectById(42L)).thenReturn(productSoldBy(9L));

        assertThatThrownBy(() -> orderService.getOrderForUser(1, 5))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void aMissingOrderIsNotFound() {
        assertThatThrownBy(() -> orderService.getOrderForUser(404, 7))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    private static Order order(String status) {
        Order order = new Order();
        order.setId(1);
        order.setUserId(7);
        order.setStatus(status);
        order.setTotalAmount(new BigDecimal("20.00"));
        return order;
    }

    private static OrderItem item() {
        OrderItem item = new OrderItem();
        item.setOrderId(1);
        item.setProductId(42);
        item.setQuantity(1);
        item.setPrice(new BigDecimal("20.00"));
        return item;
    }

    private static Product productSoldBy(Long sellerId) {
        Product product = new Product();
        product.setId(42L);
        product.setUserId(sellerId);
        return product;
    }
}
