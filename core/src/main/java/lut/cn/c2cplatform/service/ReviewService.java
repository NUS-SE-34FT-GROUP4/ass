package lut.cn.c2cplatform.service;

import lut.cn.c2cplatform.dto.ReviewRequest;
import lut.cn.c2cplatform.dto.ReviewResponse;
import lut.cn.c2cplatform.entity.Review;

import java.util.List;

public interface ReviewService {

    /**
     * Create a review
     */
    Review createReview(Integer userId, ReviewRequest request);

    /**
     * Check whether an order has been reviewed
     */
    boolean hasReviewed(Integer orderId);

    /**
     * Get all reviews for a product
     */
    List<ReviewResponse> getProductReviews(Long productId);

    /**
     * Get reviews received as a seller
     */
    List<ReviewResponse> getSellerReviews(Integer sellerId);

    /**
     * Get reviews received as a buyer
     */
    List<ReviewResponse> getBuyerReviews(Integer buyerId);

    /**
     * Get a product's average rating
     */
    Double getProductAverageRating(Long productId);
}

