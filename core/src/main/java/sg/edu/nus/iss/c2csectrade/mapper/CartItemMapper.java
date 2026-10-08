package sg.edu.nus.iss.c2csectrade.mapper;

import sg.edu.nus.iss.c2csectrade.entity.CartItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CartItemMapper {
    void insert(CartItem cartItem);

    void update(CartItem cartItem);

    void deleteById(@Param("id") Long id);

    void deleteByUserIdAndProductId(@Param("userId") Long userId, @Param("productId") Long productId);

    CartItem selectByUserIdAndProductId(@Param("userId") Long userId, @Param("productId") Long productId);

    List<CartItem> selectByUserId(@Param("userId") Long userId);

    int countByUserId(@Param("userId") Long userId);

    void deleteByUserId(@Param("userId") Long userId);
}

