package sg.edu.nus.iss.c2csectrade.service;

import sg.edu.nus.iss.c2csectrade.dto.PaymentRequest;
import sg.edu.nus.iss.c2csectrade.entity.Order;
import java.util.List;

public interface OrderService {
    Order createOrderFromCart(Integer userId);
    Order createOrderForProduct(Integer userId, Long productId, Integer quantity);
    Order createBargainOrder(Integer userId, Long productId, Integer quantity, java.math.BigDecimal customPrice);
    Order payOrder(Integer orderId, Integer userId, PaymentRequest paymentRequest);
    Order confirmOrder(Integer orderId, Integer userId);
    Order cancelOrder(Integer orderId, Integer userId);
    List<Order> getUserOrders(Integer userId);
    List<Order> getSellerOrders(Integer sellerId);
    Order getOrderForUser(Integer orderId, Integer userId);
}
