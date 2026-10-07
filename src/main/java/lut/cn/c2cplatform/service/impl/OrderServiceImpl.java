package lut.cn.c2cplatform.service.impl;

import lut.cn.c2cplatform.dto.PaymentRequest;
import lut.cn.c2cplatform.entity.*;
import lut.cn.c2cplatform.mapper.*;
import lut.cn.c2cplatform.service.CreditScoreService;
import lut.cn.c2cplatform.service.OrderService;
import lut.cn.c2cplatform.service.HybridRecommendationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @Autowired
    private TransactionMapper transactionMapper;

    @Autowired
    private CartItemMapper cartItemMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private lut.cn.c2cplatform.mapper.UserMapper userMapper;

    @Autowired
    private lut.cn.c2cplatform.mapper.BargainActivityMapper bargainActivityMapper;

    @Autowired
    private CreditScoreService creditScoreService;

    @Autowired(required = false)
    private HybridRecommendationService hybridRecommendationService;

    @Override
    @Transactional
    public Order createOrderFromCart(Integer userId) {
        // Cancel all of this user's expired PENDING orders first
        cancelExpiredPendingOrders(userId);

        List<CartItem> cartItems = cartItemMapper.selectByUserId(userId.longValue());
        if (cartItems.isEmpty()) {
            throw new RuntimeException("Your cart is empty");
        }

        BigDecimal totalAmount = BigDecimal.ZERO;

        // Lock every product and check stock first
        for (CartItem cartItem : cartItems) {
            Product product = productMapper.selectByIdForUpdate(cartItem.getProductId());
            if (product == null) {
                throw new RuntimeException("Product not found: " + cartItem.getProductId());
            }
            if (product.getStock() < cartItem.getQuantity()) {
                throw new RuntimeException("Not enough stock for \"" + product.getName() + "\". In stock: " + product.getStock());
            }
            totalAmount = totalAmount.add(product.getPrice().multiply(new BigDecimal(cartItem.getQuantity())));
        }

        // Note: stock is not deducted when the order is created, only after payment succeeds
        // so unpaid orders do not hold stock and bargains are not wrongly marked as failed

        Order order = new Order();
        order.setUserId(userId);
        order.setTotalAmount(totalAmount);
        order.setStatus("PENDING");

        // The order expires in 15 minutes
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.add(java.util.Calendar.MINUTE, 15);
        order.setExpireTime(calendar.getTime());

        order.setCreatedAt(new Date());
        order.setUpdatedAt(new Date());
        orderMapper.insert(order);

        for (CartItem cartItem : cartItems) {
            Product product = productMapper.selectById(cartItem.getProductId());
            OrderItem orderItem = new OrderItem();
            orderItem.setOrderId(order.getId());
            orderItem.setProductId(product.getId().intValue());
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setPrice(product.getPrice());
            orderItem.setFromCart(true); // Came from the cart
            orderItemMapper.insert(orderItem);
        }

        // Keep the cart for now; it is cleared after payment or when the order expires
        // cartItemMapper.deleteByUserId(userId.longValue());

        return order;
    }

    @Override
    @Transactional
    public Order createOrderForProduct(Integer userId, Long productId, Integer quantity) {
        // Cancel all of this user's expired PENDING orders first
        cancelExpiredPendingOrders(userId);

        // Select the product with a row lock (prevents concurrent overselling)
        Product product = productMapper.selectByIdForUpdate(productId);
        if (product == null) {
            throw new RuntimeException("Product not found");
        }

        // Check stock (deducted only after payment succeeds)
        if (product.getStock() < quantity) {
            throw new RuntimeException("Not enough stock. In stock: " + product.getStock());
        }

        // Note: stock is not deducted when the order is created, only after payment succeeds
        // so unpaid orders do not hold stock and bargains are not wrongly marked as failed

        // Total price
        BigDecimal totalAmount = product.getPrice().multiply(new BigDecimal(quantity));

        // Create the order
        Order order = new Order();
        order.setUserId(userId);
        order.setTotalAmount(totalAmount);
        order.setStatus("PENDING");

        // The order expires in 15 minutes
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.add(java.util.Calendar.MINUTE, 15);
        order.setExpireTime(calendar.getTime());

        order.setCreatedAt(new Date());
        order.setUpdatedAt(new Date());
        orderMapper.insert(order);

        // Create the order item
        OrderItem newOrderItem = new OrderItem();
        newOrderItem.setOrderId(order.getId());
        newOrderItem.setProductId(product.getId().intValue());
        newOrderItem.setQuantity(quantity);
        newOrderItem.setPrice(product.getPrice());
        newOrderItem.setFromCart(false); // Not from the cart (direct purchase)
        orderItemMapper.insert(newOrderItem);

        return order;
    }

    @Override
    @Transactional
    public Order createBargainOrder(Integer userId, Long productId, Integer quantity, BigDecimal customPrice) {
        // Select the product with a row lock (prevents concurrent overselling)
        Product product = productMapper.selectByIdForUpdate(productId);
        if (product == null) {
            throw new RuntimeException("Product not found");
        }

        // Check stock (deducted only after payment succeeds)
        if (product.getStock() < quantity) {
            throw new RuntimeException("Not enough stock. In stock: " + product.getStock());
        }

        // Note: stock is not deducted when the order is created, only after payment succeeds
        // so unpaid orders do not hold stock and bargains are not wrongly marked as failed

        // Use the bargain price as the total
        BigDecimal totalAmount = customPrice.multiply(new BigDecimal(quantity));

        // Create the order
        Order order = new Order();
        order.setUserId(userId);
        order.setTotalAmount(totalAmount);
        order.setStatus("PENDING");

        // The order expires in 15 minutes
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.add(java.util.Calendar.MINUTE, 15);
        order.setExpireTime(calendar.getTime());

        order.setCreatedAt(new Date());
        order.setUpdatedAt(new Date());
        orderMapper.insert(order);

        // Create the order item at the bargain price
        OrderItem newOrderItem = new OrderItem();
        newOrderItem.setOrderId(order.getId());
        newOrderItem.setProductId(product.getId().intValue());
        newOrderItem.setQuantity(quantity);
        newOrderItem.setPrice(customPrice); // Bargain price
        newOrderItem.setFromCart(false); // Not from the cart (bargain purchase)
        orderItemMapper.insert(newOrderItem);

        return order;
    }

    @Override
    @Transactional
    public Order payOrder(Integer orderId, Integer userId, PaymentRequest paymentRequest) {
        Order order = orderMapper.findById(orderId);
        if (order == null || !"PENDING".equals(order.getStatus())) {
            throw new RuntimeException("Order not found or not pending");
        }

        // Check the order belongs to the user
        if (!order.getUserId().equals(userId)) {
            throw new RuntimeException("Unauthorized to pay this order");
        }

        // Check the payment password format
        if (paymentRequest.getPassword() == null || !paymentRequest.getPassword().matches("\\d{6}")) {
            throw new RuntimeException("The payment password must be 6 digits");
        }

        // Load the user and verify the payment password
        lut.cn.c2cplatform.entity.User user = userMapper.selectById(userId.longValue());
        if (user == null) {
            throw new RuntimeException("User not found");
        }

        // Load the order items and remove those that came from the cart
        List<OrderItem> orderItems = orderItemMapper.findByOrderId(orderId);
        for (OrderItem item : orderItems) {
            if (Boolean.TRUE.equals(item.getFromCart())) {
                // Remove the product from the cart
                cartItemMapper.deleteByUserIdAndProductId(userId.longValue(), item.getProductId().longValue());
            }
        }

        // Check the user has set a payment password
        if (user.getPaymentPasswordHash() == null || user.getPaymentPasswordHash().trim().isEmpty()) {
            throw new RuntimeException("Please set a payment password first");
        }

        // Verify the payment password with BCrypt
        if (!org.springframework.security.crypto.bcrypt.BCrypt.checkpw(
                paymentRequest.getPassword(),
                user.getPaymentPasswordHash())) {
            throw new RuntimeException("Incorrect payment password");
        }

        // Deduct stock before completing payment (stock is deducted only on successful payment)
        for (OrderItem item : orderItems) {
            Long productId = item.getProductId().longValue();
            Integer quantity = item.getQuantity();

            // Select the product with a row lock
            Product product = productMapper.selectByIdForUpdate(productId);
            if (product == null) {
                throw new RuntimeException("Product not found");
            }

            // Update product popularity when order is paid
            if (hybridRecommendationService != null) {
                hybridRecommendationService.updatePopularity(productId, "order");
            }

            // Check stock
            if (product.getStock() < quantity) {
                throw new RuntimeException("Not enough stock for \"" + product.getName() + "\". In stock: " + product.getStock());
            }

            // Deduct stock
            int updated = productMapper.decreaseStock(productId, quantity);
            if (updated == 0) {
                throw new RuntimeException("Could not deduct stock for \"" + product.getName() + "\"; it may have just sold out");
            }

            // After deducting, if stock is 0 mark every bargain for this product as failed
            Product updatedProduct = productMapper.selectById(productId);
            if (updatedProduct != null && updatedProduct.getStock() == 0) {
                // Sold out: mark every active bargain as failed
                bargainActivityMapper.markAllActiveAsFailed(productId);
            }
        }

        // When paying from balance, check the balance covers it and deduct it
        if ("balance".equals(paymentRequest.getPaymentMethod())) {
            java.math.BigDecimal currentBalance = user.getBalance() != null ? user.getBalance() : java.math.BigDecimal.ZERO;

            // Check the balance is sufficient
            if (currentBalance.compareTo(order.getTotalAmount()) < 0) {
                throw new RuntimeException("Insufficient balance. Balance: ¥" + currentBalance.toPlainString() +
                                         ", amount due: ¥" + order.getTotalAmount().toPlainString());
            }

            // Deduct the balance
            user.setBalance(currentBalance.subtract(order.getTotalAmount()));
            user.setUpdatedAt(java.time.Instant.now());
            userMapper.update(user);
        }

        order.setStatus("PAID");
        order.setPaymentMethod(paymentRequest.getPaymentMethod());
        order.setUpdatedAt(new Date());
        orderMapper.updateStatus(order);

        Transaction transaction = new Transaction();
        transaction.setOrderId(orderId);
        transaction.setAmount(order.getTotalAmount());
        transaction.setTransactionType("PAYMENT");
        transaction.setPaymentMethod(paymentRequest.getPaymentMethod());
        transaction.setStatus("SUCCESS");
        transaction.setCreatedAt(new Date());
        transactionMapper.insert(transaction);

        return order;
    }

    @Override
    @Transactional
    public Order confirmOrder(Integer orderId) {
        Order order = orderMapper.findById(orderId);
        if (order == null) {
            throw new RuntimeException("Order not found");
        }
        if (!"PAID".equals(order.getStatus()) && !"DELIVERED".equals(order.getStatus())) {
            throw new RuntimeException("Only paid orders can be confirmed as received");
        }

        // Load the order items, find every seller and credit their balance
        List<OrderItem> orderItems = orderItemMapper.findByOrderId(orderId);
        for (OrderItem item : orderItems) {
            Product product = productMapper.selectById(item.getProductId().longValue());
            if (product != null) {
                // Load the seller
                lut.cn.c2cplatform.entity.User seller = userMapper.selectById(product.getUserId());
                if (seller != null) {
                    // Total for this item
                    java.math.BigDecimal itemTotal = item.getPrice().multiply(new java.math.BigDecimal(item.getQuantity()));

                    // Check the amount is valid
                    if (itemTotal.compareTo(java.math.BigDecimal.ZERO) <= 0) {
                        throw new RuntimeException("Invalid order amount; cannot confirm receipt");
                    }

                    // Credit the seller's balance
                    java.math.BigDecimal currentBalance = seller.getBalance() != null ? seller.getBalance() : java.math.BigDecimal.ZERO;
                    java.math.BigDecimal newBalance = currentBalance.add(itemTotal);

                    // Check the balance stays within the limit (DECIMAL(15,2) maximum)
                    java.math.BigDecimal maxBalance = new java.math.BigDecimal("9999999999999.99");
                    if (newBalance.compareTo(maxBalance) > 0) {
                        throw new RuntimeException("Balance exceeds the system limit. Please contact an administrator");
                    }

                    seller.setBalance(newBalance);
                    seller.setUpdatedAt(java.time.Instant.now());
                    userMapper.update(seller);

                    // Update the seller's credit score
                    creditScoreService.updateCreditScore(product.getUserId());
                }
            }
        }

        // Update the buyer's credit score
        creditScoreService.updateCreditScore(order.getUserId().longValue());

        order.setStatus("COMPLETED");
        order.setUpdatedAt(new Date());
        orderMapper.updateStatus(order);

        return order;
    }

    @Override
    @Transactional
    public Order cancelOrder(Integer orderId) {
        Order order = orderMapper.findById(orderId);
        if (order == null || !"PENDING".equals(order.getStatus())) {
            throw new RuntimeException("Order not found or not pending");
        }

        // Load the order items
        List<OrderItem> orderItems = orderItemMapper.findByOrderId(orderId);

        // Note: stock was not deducted when the order was created, so cancelling does not restore it
        // Only the cart is restored
        for (OrderItem item : orderItems) {

            // If the product came from the cart, put it back
            if (Boolean.TRUE.equals(item.getFromCart())) {
                CartItem existingCartItem = cartItemMapper.selectByUserIdAndProductId(
                    order.getUserId().longValue(),
                    item.getProductId().longValue()
                );

                if (existingCartItem != null) {
                    // Already in the cart: update the quantity
                    existingCartItem.setQuantity(existingCartItem.getQuantity() + item.getQuantity());
                    existingCartItem.setUpdatedAt(java.time.Instant.now());
                    cartItemMapper.update(existingCartItem);
                } else {
                    // Not in the cart: add it
                    CartItem cartItem = new CartItem();
                    cartItem.setUserId(order.getUserId().longValue());
                    cartItem.setProductId(item.getProductId().longValue());
                    cartItem.setQuantity(item.getQuantity());
                    cartItem.setCreatedAt(java.time.Instant.now());
                    cartItem.setUpdatedAt(java.time.Instant.now());
                    cartItemMapper.insert(cartItem);
                }
            }
        }

        // Update the order status
        order.setStatus("CANCELED");
        order.setUpdatedAt(new Date());
        orderMapper.updateStatus(order);

        return order;
    }

    @Override
    public List<Order> getUserOrders(Integer userId) {
        return orderMapper.findByUserId(userId);
    }

    @Override
    public List<Order> getSellerOrders(Integer sellerId) {
        return orderMapper.findBySellerId(sellerId);
    }

    @Override
    public Order getOrderById(Integer orderId) {
        return orderMapper.findById(orderId);
    }

    /**
     * Cancel all of the user's expired PENDING orders
     * Called before creating a new order to avoid duplicates
     */
    private void cancelExpiredPendingOrders(Integer userId) {
        List<Order> userOrders = orderMapper.findByUserId(userId);
        Date now = new Date();

        for (Order order : userOrders) {
            // Cancel automatically if the order is PENDING and has expired
            if ("PENDING".equals(order.getStatus()) && order.getExpireTime() != null && order.getExpireTime().before(now)) {
                try {
                    // Reuse the existing cancel logic
                    cancelOrder(order.getId());
                } catch (Exception e) {
                    // If cancelling fails, log it and continue (does not block the new order)
                    System.err.println("Failed to cancel expired order: " + order.getId() + ", error: " + e.getMessage());
                }
            }
        }
    }
}
