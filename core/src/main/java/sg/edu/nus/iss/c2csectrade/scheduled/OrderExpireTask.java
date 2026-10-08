package sg.edu.nus.iss.c2csectrade.scheduled;

import sg.edu.nus.iss.c2csectrade.entity.CartItem;
import sg.edu.nus.iss.c2csectrade.entity.Order;
import sg.edu.nus.iss.c2csectrade.entity.OrderItem;
import sg.edu.nus.iss.c2csectrade.mapper.CartItemMapper;
import sg.edu.nus.iss.c2csectrade.mapper.OrderItemMapper;
import sg.edu.nus.iss.c2csectrade.mapper.OrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Closes expired orders automatically
 * Runs every minute and closes pending orders that have expired
 * If an order came from the cart, its items are returned to the cart
 */
@Component
public class OrderExpireTask {

    private static final Logger logger = LoggerFactory.getLogger(OrderExpireTask.class);

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @Autowired
    private CartItemMapper cartItemMapper;

    @Autowired
    private sg.edu.nus.iss.c2csectrade.mapper.ProductMapper productMapper;

    /**
     * Runs every minute to find and close expired orders
     * Scheduled with @Scheduled
     */
    @Scheduled(fixedRate = 60000) // Every 60 seconds
    @Transactional
    public void closeExpiredOrders() {
        try {
            // Find all expired pending orders
            List<Order> expiredOrders = orderMapper.findExpiredOrders();

            if (expiredOrders.isEmpty()) {
                return;
            }

            logger.info("Found {} expired orders, processing...", expiredOrders.size());

            for (Order order : expiredOrders) {
                try {
                    // Load the order items
                    List<OrderItem> orderItems = orderItemMapper.findByOrderId(order.getId());

                    // Restore stock and cart items
                    for (OrderItem item : orderItems) {
                        // Restore stock
                        try {
                            sg.edu.nus.iss.c2csectrade.entity.Product product = productMapper.selectById(item.getProductId().longValue());
                            if (product != null) {
                                product.setStock(product.getStock() + item.getQuantity());
                                product.setUpdatedAt(java.time.LocalDateTime.now());
                                productMapper.update(product);
                                logger.info("Order {}: restored {} units of product {}", order.getId(), item.getQuantity(), item.getProductId());
                            }
                        } catch (Exception e) {
                            logger.error("Failed to restore stock for product {}: {}", item.getProductId(), e.getMessage());
                        }

                        // Restore items that came from the cart
                        if (Boolean.TRUE.equals(item.getFromCart())) {
                            // Check whether the cart already has this product
                            CartItem existingCartItem = cartItemMapper.selectByUserIdAndProductId(
                                order.getUserId().longValue(),
                                item.getProductId().longValue()
                            );

                            if (existingCartItem != null) {
                                // Already in the cart: update the quantity
                                existingCartItem.setQuantity(existingCartItem.getQuantity() + item.getQuantity());
                                existingCartItem.setUpdatedAt(Instant.now());
                                cartItemMapper.update(existingCartItem);
                                logger.info("Order {}: product {} returned to cart (quantity updated)", order.getId(), item.getProductId());
                            } else {
                                // Not in the cart: add it again
                                CartItem cartItem = new CartItem();
                                cartItem.setUserId(order.getUserId().longValue());
                                cartItem.setProductId(item.getProductId().longValue());
                                cartItem.setQuantity(item.getQuantity());
                                cartItem.setCreatedAt(Instant.now());
                                cartItem.setUpdatedAt(Instant.now());
                                cartItemMapper.insert(cartItem);
                                logger.info("Order {}: product {} returned to cart (added)", order.getId(), item.getProductId());
                            }
                        }
                    }

                    logger.info("Order {} expired; stock and cart restored", order.getId());
                } catch (Exception e) {
                    logger.error("Failed to process expired order {}: {}", order.getId(), e.getMessage(), e);
                }
            }

            // Mark the orders EXPIRED in one batch
            List<Integer> orderIds = expiredOrders.stream()
                .map(Order::getId)
                .collect(Collectors.toList());

            orderMapper.batchUpdateStatusToExpired(orderIds);

            logger.info("Closed {} expired orders", orderIds.size());

        } catch (Exception e) {
            logger.error("Expired order task failed: {}", e.getMessage(), e);
        }
    }
}

