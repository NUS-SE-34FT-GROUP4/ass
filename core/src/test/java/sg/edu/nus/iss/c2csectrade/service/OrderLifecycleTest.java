package sg.edu.nus.iss.c2csectrade.service;

import sg.edu.nus.iss.c2csectrade.dto.PaymentRequest;
import sg.edu.nus.iss.c2csectrade.entity.CartItem;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCrypt;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Creating, paying for and confirming orders. Stock is checked when an order
 * is created and deducted only when it is paid; confirming receipt releases
 * escrow to the seller. Buyer is user 7, the seller of product 42 is user 9.
 */
@ExtendWith(MockitoExtension.class)
class OrderLifecycleTest {

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
    void buyingNowCreatesAPendingOrderWithoutDeductingStock() {
        when(orderMapper.findByUserId(7)).thenReturn(List.of());
        when(productMapper.selectByIdForUpdate(42L)).thenReturn(product(5));

        Order order = orderService.createOrderForProduct(7, 42L, 2);

        assertThat(order.getStatus()).isEqualTo("PENDING");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("20.00");
        assertThat(order.getExpireTime()).isAfter(new Date());
        ArgumentCaptor<OrderItem> item = ArgumentCaptor.forClass(OrderItem.class);
        verify(orderItemMapper).insert(item.capture());
        assertThat(item.getValue().getFromCart()).isFalse();
        verify(productMapper, never()).decreaseStock(any(), any());
    }

    @Test
    void anExpiredOrderIsCancelledBeforeANewOneIsCreated() {
        Order expired = order("PENDING");
        expired.setExpireTime(new Date(System.currentTimeMillis() - 60_000));
        Order current = order("PENDING");
        current.setId(2);
        current.setExpireTime(new Date(System.currentTimeMillis() + 60_000));
        when(orderMapper.findByUserId(7)).thenReturn(List.of(expired, current, order("PAID")));
        when(orderMapper.updateStatusIfPending(expired)).thenReturn(1);
        when(productMapper.selectByIdForUpdate(42L)).thenReturn(product(5));

        orderService.createOrderForProduct(7, 42L, 1);

        assertThat(expired.getStatus()).isEqualTo("CANCELED");
        assertThat(current.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void aFailedCancelOfAnExpiredOrderDoesNotBlockTheNewOne() {
        Order expired = order("PENDING");
        expired.setExpireTime(new Date(System.currentTimeMillis() - 60_000));
        when(orderMapper.findByUserId(7)).thenReturn(List.of(expired));
        when(orderMapper.updateStatusIfPending(any())).thenReturn(0);
        when(productMapper.selectByIdForUpdate(42L)).thenReturn(product(5));

        assertThat(orderService.createOrderForProduct(7, 42L, 1).getStatus()).isEqualTo("PENDING");
    }

    @Test
    void buyingMoreThanIsInStockFails() {
        when(orderMapper.findByUserId(7)).thenReturn(List.of());
        when(productMapper.selectByIdForUpdate(42L)).thenReturn(product(1));

        assertThatThrownBy(() -> orderService.createOrderForProduct(7, 42L, 2))
                .hasMessageContaining("Not enough stock");
        verify(orderMapper, never()).insert(any());
    }

    @Test
    void buyingAnUnknownProductFails() {
        when(orderMapper.findByUserId(7)).thenReturn(List.of());
        when(productMapper.selectByIdForUpdate(42L)).thenReturn(null);

        assertThatThrownBy(() -> orderService.createOrderForProduct(7, 42L, 1))
                .hasMessageContaining("Product not found");
    }

    @Test
    void aBargainOrderIsPricedAtTheAgreedPrice() {
        when(productMapper.selectByIdForUpdate(42L)).thenReturn(product(5));

        Order order = orderService.createBargainOrder(7, 42L, 2, new BigDecimal("6.50"));

        assertThat(order.getTotalAmount()).isEqualByComparingTo("13.00");
        ArgumentCaptor<OrderItem> item = ArgumentCaptor.forClass(OrderItem.class);
        verify(orderItemMapper).insert(item.capture());
        assertThat(item.getValue().getPrice()).isEqualByComparingTo("6.50");
    }

    @Test
    void aBargainOrderStillChecksTheProductAndItsStock() {
        when(productMapper.selectByIdForUpdate(42L)).thenReturn(product(1)).thenReturn(null);

        assertThatThrownBy(() -> orderService.createBargainOrder(7, 42L, 2, new BigDecimal("6.50")))
                .hasMessageContaining("Not enough stock");
        assertThatThrownBy(() -> orderService.createBargainOrder(7, 42L, 2, new BigDecimal("6.50")))
                .hasMessageContaining("Product not found");
        verify(orderMapper, never()).insert(any());
    }

    @Test
    void aCartOrderCoversEveryItemInTheCart() {
        Product lamp = product(5);
        Product chair = product(3);
        chair.setId(43L);
        chair.setPrice(new BigDecimal("30.00"));
        when(orderMapper.findByUserId(7)).thenReturn(List.of());
        when(cartItemMapper.selectByUserId(7L)).thenReturn(List.of(cartItem(42L, 2), cartItem(43L, 1)));
        when(productMapper.selectByIdForUpdate(42L)).thenReturn(lamp);
        when(productMapper.selectByIdForUpdate(43L)).thenReturn(chair);
        when(productMapper.selectById(42L)).thenReturn(lamp);
        when(productMapper.selectById(43L)).thenReturn(chair);

        Order order = orderService.createOrderFromCart(7);

        assertThat(order.getTotalAmount()).isEqualByComparingTo("50.00");
        verify(orderItemMapper, times(2)).insert(argThat(OrderItem::getFromCart));
        verify(productMapper, never()).decreaseStock(any(), any());
    }

    @Test
    void anEmptyCartCannotBeOrdered() {
        when(orderMapper.findByUserId(7)).thenReturn(List.of());
        when(cartItemMapper.selectByUserId(7L)).thenReturn(List.of());

        assertThatThrownBy(() -> orderService.createOrderFromCart(7)).hasMessageContaining("cart is empty");
    }

    @Test
    void oneCartItemOutOfStockFailsTheWholeOrder() {
        when(orderMapper.findByUserId(7)).thenReturn(List.of());
        when(cartItemMapper.selectByUserId(7L)).thenReturn(List.of(cartItem(42L, 9)));
        when(productMapper.selectByIdForUpdate(42L)).thenReturn(product(5));

        assertThatThrownBy(() -> orderService.createOrderFromCart(7)).hasMessageContaining("Not enough stock");
        verify(orderMapper, never()).insert(any());
    }

    @Test
    void payingFromBalanceDeductsTheTotalAndRecordsTheTransaction() {
        User buyer = buyer(new BigDecimal("50.00"));
        when(orderMapper.findById(1)).thenReturn(order("PENDING"));
        when(userMapper.selectById(7L)).thenReturn(buyer);
        when(orderItemMapper.findByOrderId(1)).thenReturn(List.of(item(2, false)));
        when(productMapper.selectByIdForUpdate(42L)).thenReturn(product(2));
        when(productMapper.decreaseStock(42L, 2)).thenReturn(1);
        when(productMapper.selectById(42L)).thenReturn(product(0));
        when(orderMapper.updateStatusIfPending(any())).thenReturn(1);

        Order paid = orderService.payOrder(1, 7, payment("balance", "123456"));

        assertThat(paid.getStatus()).isEqualTo("PAID");
        assertThat(buyer.getBalance()).isEqualByComparingTo("30.00");
        verify(userMapper).update(buyer);
        // The last one sold, so every open bargain on it has failed
        verify(bargainActivityMapper).markAllActiveAsFailed(42L);
        verify(transactionMapper).insert(argThat(t -> "PAYMENT".equals(t.getTransactionType())
                && t.getAmount().compareTo(new BigDecimal("20.00")) == 0));
        verify(cartItemMapper, never()).deleteByUserIdAndProductId(any(), any());
    }

    @Test
    void aBalanceTooLowFailsThePayment() {
        when(orderMapper.findById(1)).thenReturn(order("PENDING"));
        when(userMapper.selectById(7L)).thenReturn(buyer(new BigDecimal("10.00")));
        when(orderItemMapper.findByOrderId(1)).thenReturn(List.of(item(2, false)));
        when(productMapper.selectByIdForUpdate(42L)).thenReturn(product(5));
        when(productMapper.decreaseStock(42L, 2)).thenReturn(1);
        when(productMapper.selectById(42L)).thenReturn(product(3));

        assertThatThrownBy(() -> orderService.payOrder(1, 7, payment("balance", "123456")))
                .hasMessageContaining("Insufficient balance");
        verify(orderMapper, never()).updateStatusIfPending(any());
    }

    @Test
    void payingFailsWhenTheStockRanOutMeanwhile() {
        when(orderMapper.findById(1)).thenReturn(order("PENDING"));
        when(userMapper.selectById(7L)).thenReturn(buyer(BigDecimal.ZERO));
        when(orderItemMapper.findByOrderId(1)).thenReturn(List.of(item(2, false)));
        when(productMapper.selectByIdForUpdate(42L)).thenReturn(product(5));
        when(productMapper.decreaseStock(42L, 2)).thenReturn(0);

        assertThatThrownBy(() -> orderService.payOrder(1, 7, payment("card", "123456")))
                .hasMessageContaining("may have just sold out");
        verify(orderMapper, never()).updateStatusIfPending(any());
    }

    @Test
    void onlyTheBuyerCanPay() {
        when(orderMapper.findById(1)).thenReturn(order("PENDING"));

        assertThatThrownBy(() -> orderService.payOrder(1, 5, payment("card", "123456")))
                .hasMessageContaining("Unauthorized");
        verifyNoInteractions(userMapper);
    }

    @Test
    void aPaidOrderCannotBePaidAgain() {
        when(orderMapper.findById(1)).thenReturn(order("PAID"));

        assertThatThrownBy(() -> orderService.payOrder(1, 7, payment("card", "123456")))
                .hasMessageContaining("not pending");
    }

    @Test
    void aPaymentPasswordMustBeSixDigits() {
        when(orderMapper.findById(1)).thenReturn(order("PENDING"));

        assertThatThrownBy(() -> orderService.payOrder(1, 7, payment("card", "12ab")))
                .hasMessageContaining("6 digits");
        verifyNoInteractions(userMapper);
    }

    @Test
    void aWrongPaymentPasswordFails() {
        when(orderMapper.findById(1)).thenReturn(order("PENDING"));
        when(userMapper.selectById(7L)).thenReturn(buyer(BigDecimal.ZERO));
        when(orderItemMapper.findByOrderId(1)).thenReturn(List.of());

        assertThatThrownBy(() -> orderService.payOrder(1, 7, payment("card", "654321")))
                .hasMessageContaining("Incorrect payment password");
        verify(productMapper, never()).decreaseStock(any(), any());
    }

    @Test
    void aBuyerWithoutAPaymentPasswordMustSetOneFirst() {
        User buyer = buyer(BigDecimal.ZERO);
        buyer.setPaymentPasswordHash(null);
        when(orderMapper.findById(1)).thenReturn(order("PENDING"));
        when(userMapper.selectById(7L)).thenReturn(buyer);
        when(orderItemMapper.findByOrderId(1)).thenReturn(List.of());

        assertThatThrownBy(() -> orderService.payOrder(1, 7, payment("card", "123456")))
                .hasMessageContaining("set a payment password");
    }

    @Test
    void confirmingReceiptPaysTheSellerAndCompletesTheOrder() {
        User seller = seller(new BigDecimal("5.00"));
        when(orderMapper.findById(1)).thenReturn(order("PAID"));
        when(orderItemMapper.findByOrderId(1)).thenReturn(List.of(item(2, false)));
        when(productMapper.selectById(42L)).thenReturn(product(0));
        when(userMapper.selectById(9L)).thenReturn(seller);

        Order done = orderService.confirmOrder(1, 7);

        assertThat(done.getStatus()).isEqualTo("COMPLETED");
        assertThat(seller.getBalance()).isEqualByComparingTo("25.00");
        verify(userMapper).update(seller);
        verify(creditScoreService).updateCreditScore(9L);
        verify(creditScoreService).updateCreditScore(7L);
        verify(orderMapper).updateStatus(done);
    }

    @Test
    void aSellerWithNoBalanceYetIsCreditedFromZero() {
        User seller = seller(null);
        when(orderMapper.findById(1)).thenReturn(order("PAID"));
        when(orderItemMapper.findByOrderId(1)).thenReturn(List.of(item(2, false)));
        when(productMapper.selectById(42L)).thenReturn(product(0));
        when(userMapper.selectById(9L)).thenReturn(seller);

        orderService.confirmOrder(1, 7);

        assertThat(seller.getBalance()).isEqualByComparingTo("20.00");
    }

    @Test
    void anOrderThatIsNotPaidCannotBeConfirmed() {
        when(orderMapper.findById(1)).thenReturn(order("PENDING"));

        assertThatThrownBy(() -> orderService.confirmOrder(1, 7)).hasMessageContaining("Only paid orders");
        verifyNoInteractions(userMapper, creditScoreService);
    }

    @Test
    void anUnknownOrderCannotBeConfirmed() {
        when(orderMapper.findById(1)).thenReturn(null);

        assertThatThrownBy(() -> orderService.confirmOrder(1, 7)).hasMessageContaining("Order not found");
    }

    @Test
    void anItemWithNoAmountCannotBeConfirmed() {
        OrderItem free = item(2, false);
        free.setPrice(BigDecimal.ZERO);
        when(orderMapper.findById(1)).thenReturn(order("PAID"));
        when(orderItemMapper.findByOrderId(1)).thenReturn(List.of(free));
        when(productMapper.selectById(42L)).thenReturn(product(0));
        when(userMapper.selectById(9L)).thenReturn(seller(BigDecimal.ZERO));

        assertThatThrownBy(() -> orderService.confirmOrder(1, 7)).hasMessageContaining("Invalid order amount");
        verify(orderMapper, never()).updateStatus(any());
    }

    @Test
    void aSellerBalanceOverTheLimitIsRejected() {
        when(orderMapper.findById(1)).thenReturn(order("PAID"));
        when(orderItemMapper.findByOrderId(1)).thenReturn(List.of(item(2, false)));
        when(productMapper.selectById(42L)).thenReturn(product(0));
        when(userMapper.selectById(9L)).thenReturn(seller(new BigDecimal("9999999999999.99")));

        assertThatThrownBy(() -> orderService.confirmOrder(1, 7)).hasMessageContaining("system limit");
        verify(userMapper, never()).update(any());
    }

    @Test
    void ordersAreListedForTheBuyerAndForTheSeller() {
        List<Order> orders = List.of(order("PAID"));
        when(orderMapper.findByUserId(7)).thenReturn(orders);
        when(orderMapper.findBySellerId(9)).thenReturn(orders);

        assertThat(orderService.getUserOrders(7)).isSameAs(orders);
        assertThat(orderService.getSellerOrders(9)).isSameAs(orders);
    }

    private static Order order(String status) {
        Order order = new Order();
        order.setId(1);
        order.setUserId(7);
        order.setStatus(status);
        order.setTotalAmount(new BigDecimal("20.00"));
        return order;
    }

    private static Product product(int stock) {
        Product product = new Product();
        product.setId(42L);
        product.setUserId(9L);
        product.setName("Desk lamp");
        product.setPrice(new BigDecimal("10.00"));
        product.setStock(stock);
        return product;
    }

    private static OrderItem item(int quantity, boolean fromCart) {
        OrderItem item = new OrderItem();
        item.setOrderId(1);
        item.setProductId(42);
        item.setQuantity(quantity);
        item.setPrice(new BigDecimal("10.00"));
        item.setFromCart(fromCart);
        return item;
    }

    private static CartItem cartItem(Long productId, int quantity) {
        CartItem item = new CartItem();
        item.setUserId(7L);
        item.setProductId(productId);
        item.setQuantity(quantity);
        return item;
    }

    private static User buyer(BigDecimal balance) {
        User user = new User();
        user.setId(7L);
        user.setPaymentPasswordHash(BCrypt.hashpw("123456", BCrypt.gensalt(4)));
        user.setBalance(balance);
        return user;
    }

    private static User seller(BigDecimal balance) {
        User user = new User();
        user.setId(9L);
        user.setBalance(balance);
        return user;
    }

    private static PaymentRequest payment(String method, String password) {
        PaymentRequest request = new PaymentRequest();
        request.setPaymentMethod(method);
        request.setPassword(password);
        return request;
    }
}
