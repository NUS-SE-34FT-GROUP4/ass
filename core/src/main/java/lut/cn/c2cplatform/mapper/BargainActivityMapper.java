package lut.cn.c2cplatform.mapper;

import lut.cn.c2cplatform.entity.BargainActivity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BargainActivityMapper {
    void insert(BargainActivity bargainActivity);
    BargainActivity selectById(Long id);
    List<BargainActivity> selectByUserId(@Param("userId") Long userId);
    List<BargainActivity> selectByProductId(@Param("productId") Long productId);
    void update(BargainActivity bargainActivity);
    void updateStatus(@Param("id") Long id, @Param("status") String status);
    List<BargainActivity> selectExpiredActivities();

    // Check whether the user has already started a bargain for this product
    BargainActivity selectActiveByUserAndProduct(@Param("userId") Long userId, @Param("productId") Long productId);

    // Mark every active bargain for the product as failed (called when the product is sold)
    void markAllActiveAsFailed(@Param("productId") Long productId);
}

