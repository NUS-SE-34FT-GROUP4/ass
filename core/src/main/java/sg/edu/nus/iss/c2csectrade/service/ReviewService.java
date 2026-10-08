package sg.edu.nus.iss.c2csectrade.service;

import sg.edu.nus.iss.c2csectrade.dto.ReviewRequest;
import sg.edu.nus.iss.c2csectrade.dto.ReviewResponse;
import sg.edu.nus.iss.c2csectrade.entity.Review;

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

