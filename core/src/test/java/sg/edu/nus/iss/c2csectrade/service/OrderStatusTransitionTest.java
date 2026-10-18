package sg.edu.nus.iss.c2csectrade.service;

import sg.edu.nus.iss.c2csectrade.dto.PaymentRequest;
import sg.edu.nus.iss.c2csectrade.entity.Order;
import sg.edu.nus.iss.c2csectrade.entity.OrderItem;
import sg.edu.nus.iss.c2csectrade.entity.Product;
import sg.edu.nus.iss.c2csectrade.entity.User;
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
import org.springframework.security.crypto.bcrypt.BCrypt;

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
 * An order leaves PENDING once. Stock is deducted only on payment and the cart
 * is kept until payment, so cancelling undoes nothing and a payment that loses
 * the race to expiry must fail (rolling back its stock deduction).
 */
@ExtendWith(MockitoExtension.class)
class OrderStatusTransitionTest {

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
    void cancellingLeavesTheCartAndStockAlone() {
        when(orderMapper.findById(1)).thenReturn(pendingOrder());
        when(orderMapper.updateStatusIfPending(any())).thenReturn(1);

        Order cancelled = orderService.cancelOrder(1, 7);

        assertThat(cancelled.getStatus()).isEqualTo("CANCELED");
        verifyNoInteractions(cartItemMapper, productMapper);
    }

    @Test
    void cancellingFailsIfTheOrderLeftPendingMeanwhile() {
        when(orderMapper.findById(1)).thenReturn(pendingOrder());
        when(orderMapper.updateStatusIfPending(any())).thenReturn(0);

        assertThatThrownBy(() -> orderService.cancelOrder(1, 7))
                .hasMessageContaining("not pending");
    }

    @Test
    void paymentThatLosesTheRaceToExpiryFails() {
        when(orderMapper.findById(1)).thenReturn(pendingOrder());
        when(userMapper.selectById(7L)).thenReturn(buyer());
        when(orderItemMapper.findByOrderId(1)).thenReturn(List.of(item()));
        when(productMapper.selectByIdForUpdate(42L)).thenReturn(product(5));
        when(productMapper.decreaseStock(42L, 2)).thenReturn(1);
        when(productMapper.selectById(42L)).thenReturn(product(3));
        when(orderMapper.updateStatusIfPending(any())).thenReturn(0);

        assertThatThrownBy(() -> orderService.payOrder(1, 7, payment()))
                .hasMessageContaining("no longer pending");
        verify(transactionMapper, never()).insert(any());
    }

    @Test
    void paymentOfAPendingOrderSucceeds() {
        when(orderMapper.findById(1)).thenReturn(pendingOrder());
        when(userMapper.selectById(7L)).thenReturn(buyer());
        when(orderItemMapper.findByOrderId(1)).thenReturn(List.of(item()));
        when(productMapper.selectByIdForUpdate(42L)).thenReturn(product(5));
        when(productMapper.decreaseStock(42L, 2)).thenReturn(1);
        when(productMapper.selectById(42L)).thenReturn(product(3));
        when(orderMapper.updateStatusIfPending(any())).thenReturn(1);

        Order paid = orderService.payOrder(1, 7, payment());

        assertThat(paid.getStatus()).isEqualTo("PAID");
        verify(transactionMapper).insert(any());
    }

    private static Order pendingOrder() {
        Order order = new Order();
        order.setId(1);
        order.setUserId(7);
        order.setStatus("PENDING");
        order.setTotalAmount(new BigDecimal("20.00"));
        return order;
    }

    private static User buyer() {
        User user = new User();
        user.setId(7L);
        user.setPaymentPasswordHash(BCrypt.hashpw("123456", BCrypt.gensalt(4)));
        return user;
    }

    private static OrderItem item() {
        OrderItem item = new OrderItem();
        item.setOrderId(1);
        item.setProductId(42);
        item.setQuantity(2);
        item.setFromCart(true);
        return item;
    }

    private static Product product(int stock) {
        Product product = new Product();
        product.setId(42L);
        product.setName("Desk lamp");
        product.setStock(stock);
        return product;
    }

    private static PaymentRequest payment() {
        PaymentRequest request = new PaymentRequest();
        request.setPassword("123456");
        request.setPaymentMethod("card");
        return request;
    }
}
