package sg.edu.nus.iss.c2csectrade.mapper;

import sg.edu.nus.iss.c2csectrade.entity.OrderItem;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderItemMapper {
    void insert(OrderItem orderItem);
    java.util.List<OrderItem> findByOrderId(Integer orderId);
}
