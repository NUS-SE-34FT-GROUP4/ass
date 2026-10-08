package sg.edu.nus.iss.c2csectrade.service.impl;

import sg.edu.nus.iss.c2csectrade.dto.ReviewRequest;
import sg.edu.nus.iss.c2csectrade.dto.ReviewResponse;
import sg.edu.nus.iss.c2csectrade.entity.*;
import sg.edu.nus.iss.c2csectrade.mapper.*;
import sg.edu.nus.iss.c2csectrade.service.CreditScoreService;
import sg.edu.nus.iss.c2csectrade.service.ReviewService;
import sg.edu.nus.iss.c2csectrade.service.HybridRecommendationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ReviewServiceImpl implements ReviewService {

    @Autowired
    private ReviewMapper reviewMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private CreditScoreService creditScoreService;

    @Autowired(required = false)
    private HybridRecommendationService hybridRecommendationService;

    @Override
    @Transactional
    public Review createReview(Integer userId, ReviewRequest request) {
        // Check the order
        Order order = orderMapper.findById(request.getOrderId());
        if (order == null) {
            throw new RuntimeException("Order not found");
        }

        // Check the order belongs to the user
        if (!order.getUserId().equals(userId)) {
            throw new RuntimeException("You are not allowed to review this order");
        }

        // Check the order status (only completed orders can be reviewed)
        if (!"DELIVERED".equals(order.getStatus()) && !"COMPLETED".equals(order.getStatus())) {
            throw new RuntimeException("The order is not completed and cannot be reviewed");
        }

        // Check whether it has already been reviewed
        Review existingReview = reviewMapper.selectByOrderId(request.getOrderId());
        if (existingReview != null) {
            throw new RuntimeException("This order has already been reviewed");
        }

        // Check the rating range
        if (request.getProductRating() < 1 || request.getProductRating() > 5) {
            throw new RuntimeException("Product rating must be between 1 and 5");
        }
        if (request.getSellerRating() < 1 || request.getSellerRating() > 5) {
            throw new RuntimeException("Seller rating must be between 1 and 5");
        }

        // Load the order item (assumes one product per order; change this if orders can hold several)
        List<OrderItem> orderItems = orderItemMapper.findByOrderId(request.getOrderId());
        if (orderItems.isEmpty()) {
            throw new RuntimeException("Order item not found");
        }

        OrderItem orderItem = orderItems.get(0);
        Product product = productMapper.selectById(orderItem.getProductId().longValue());
        if (product == null) {
            throw new RuntimeException("Product not found");
        }

        // Create the review
        Review review = new Review();
        review.setOrderId(request.getOrderId());
        review.setProductId(product.getId());
        review.setBuyerId(userId);
        review.setSellerId(product.getUserId().intValue());
        review.setProductRating(request.getProductRating());
        review.setSellerRating(request.getSellerRating());
        review.setComment(request.getComment());
        review.setIsAnonymous(request.getIsAnonymous() != null ? request.getIsAnonymous() : false);

        // Handle review images
        if (request.getReviewImages() != null && !request.getReviewImages().isEmpty()) {
            review.setReviewImages(String.join(",", request.getReviewImages()));
        }

        Instant now = Instant.now();
        review.setCreatedAt(now);
        review.setUpdatedAt(now);
        reviewMapper.insert(review);

        // Update product popularity when reviewed
        if (hybridRecommendationService != null) {
            hybridRecommendationService.updatePopularity(product.getId(), "review");
        }

        // Update the seller's credit score
        creditScoreService.updateCreditScore(product.getUserId());

        return review;
    }

    @Override
    public boolean hasReviewed(Integer orderId) {
        Review review = reviewMapper.selectByOrderId(orderId);
        return review != null;
    }

    @Override
    public List<ReviewResponse> getProductReviews(Long productId) {
        List<Review> reviews = reviewMapper.selectByProductId(productId);
        return reviews.stream().map(this::convertToResponse).collect(Collectors.toList());
    }

    @Override
    public List<ReviewResponse> getSellerReviews(Integer sellerId) {
        List<Review> reviews = reviewMapper.selectBySellerId(sellerId);
        return reviews.stream().map(this::convertToResponse).collect(Collectors.toList());
    }

    @Override
    public List<ReviewResponse> getBuyerReviews(Integer buyerId) {
        List<Review> reviews = reviewMapper.selectByBuyerId(buyerId);
        return reviews.stream().map(this::convertToResponse).collect(Collectors.toList());
    }

    @Override
    public Double getProductAverageRating(Long productId) {
        Double avg = reviewMapper.getAverageProductRating(productId);
        return avg != null ? avg : 0.0;
    }

    private ReviewResponse convertToResponse(Review review) {
        ReviewResponse response = new ReviewResponse();
        response.setId(review.getId());
        response.setOrderId(review.getOrderId());
        response.setProductId(review.getProductId());
        response.setSellerId(review.getSellerId());
        response.setProductRating(review.getProductRating());
        response.setSellerRating(review.getSellerRating());
        response.setComment(review.getComment());
        response.setIsAnonymous(review.getIsAnonymous());
        response.setCreatedAt(review.getCreatedAt());

        // Handle review images
        if (review.getReviewImages() != null && !review.getReviewImages().isEmpty()) {
            response.setReviewImages(Arrays.asList(review.getReviewImages().split(",")));
        } else {
            response.setReviewImages(new ArrayList<>());
        }

        // Load the product
        Product product = productMapper.selectById(review.getProductId());
        if (product != null) {
            response.setProductName(product.getName());
        }

        // Load the buyer
        if (review.getIsAnonymous()) {
            response.setBuyerId(null);
            response.setBuyerName("Anonymous");
            response.setBuyerAvatar("https://ui-avatars.com/api/?name=Anonymous&background=cccccc&color=fff&size=100");
        } else {
            User buyer = userMapper.selectById(review.getBuyerId().longValue());
            if (buyer != null) {
                response.setBuyerId(review.getBuyerId());
                response.setBuyerName(buyer.getDisplayName());
                response.setBuyerAvatar(buyer.getAvatarUrl());
            }
        }

        return response;
    }
}
