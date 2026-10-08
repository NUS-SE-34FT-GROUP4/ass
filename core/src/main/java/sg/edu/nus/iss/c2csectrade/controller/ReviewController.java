package sg.edu.nus.iss.c2csectrade.controller;

import sg.edu.nus.iss.c2csectrade.dto.ReviewRequest;
import sg.edu.nus.iss.c2csectrade.dto.ReviewResponse;
import sg.edu.nus.iss.c2csectrade.entity.Review;
import sg.edu.nus.iss.c2csectrade.entity.User;
import sg.edu.nus.iss.c2csectrade.mapper.UserMapper;
import sg.edu.nus.iss.c2csectrade.service.ReviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private UserMapper userMapper;

    /**
     * Create a review
     */
    @PostMapping
    public ResponseEntity<?> createReview(@RequestBody ReviewRequest request, Authentication authentication) {
        try {
            // Look up the user by username
            String username = authentication.getName();
            User user = userMapper.selectByUsername(username);
            if (user == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "User not found");
                return ResponseEntity.badRequest().body(response);
            }

            Integer userId = user.getId().intValue();
            Review review = reviewService.createReview(userId, request);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Review submitted");
            response.put("data", review);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Check whether an order has been reviewed
     */
    @GetMapping("/check/{orderId}")
    public ResponseEntity<?> checkReview(@PathVariable Integer orderId) {
        try {
            boolean hasReviewed = reviewService.hasReviewed(orderId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("hasReviewed", hasReviewed);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Get all reviews for a product
     */
    @GetMapping("/product/{productId}")
    public ResponseEntity<?> getProductReviews(@PathVariable Long productId) {
        try {
            List<ReviewResponse> reviews = reviewService.getProductReviews(productId);
            Double averageRating = reviewService.getProductAverageRating(productId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("reviews", reviews);
            response.put("averageRating", averageRating);
            response.put("totalReviews", reviews.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Get reviews received as a seller
     */
    @GetMapping("/seller/{sellerId}")
    public ResponseEntity<?> getSellerReviews(@PathVariable Integer sellerId) {
        try {
            List<ReviewResponse> reviews = reviewService.getSellerReviews(sellerId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("reviews", reviews);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Get reviews received as a buyer
     */
    @GetMapping("/buyer/{buyerId}")
    public ResponseEntity<?> getBuyerReviews(@PathVariable Integer buyerId) {
        try {
            List<ReviewResponse> reviews = reviewService.getBuyerReviews(buyerId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("reviews", reviews);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}

