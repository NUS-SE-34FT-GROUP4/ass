package sg.edu.nus.iss.c2csectrade.mapper;

import sg.edu.nus.iss.c2csectrade.entity.Favorite;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FavoriteMapper {
    void insert(Favorite favorite);

    void deleteByUserIdAndProductId(@Param("userId") Long userId, @Param("productId") Long productId);

    Favorite selectByUserIdAndProductId(@Param("userId") Long userId, @Param("productId") Long productId);

    List<Long> selectProductIdsByUserId(@Param("userId") Long userId);

    int countByUserId(@Param("userId") Long userId);
}

