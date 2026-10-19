package sg.edu.nus.iss.c2csectrade.controller;

import sg.edu.nus.iss.c2csectrade.dto.ReviewRequest;
import sg.edu.nus.iss.c2csectrade.dto.ReviewResponse;
import sg.edu.nus.iss.c2csectrade.entity.Review;
import sg.edu.nus.iss.c2csectrade.entity.User;
import sg.edu.nus.iss.c2csectrade.mapper.UserMapper;
import sg.edu.nus.iss.c2csectrade.service.ReviewService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Every review endpoint answers {success, ...}; a rule the service
 * rejects comes back as a 400 carrying the service's message.
 */
@ExtendWith(MockitoExtension.class)
class ReviewControllerTest {

    @Mock
    private ReviewService reviewService;
    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private ReviewController reviewController;

    private final Authentication buyer7 = new UsernamePasswordAuthenticationToken("buyer7", null);

    @Test
    void aReviewIsSubmittedAsTheSignedInBuyer() {
        User me = new User();
        me.setId(7L);
        when(userMapper.selectByUsername("buyer7")).thenReturn(me);
        ReviewRequest request = new ReviewRequest();
        Review review = new Review();
        when(reviewService.createReview(7, request)).thenReturn(review);

        ResponseEntity<?> response = reviewController.createReview(request, buyer7);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(body(response)).containsEntry("success", true).containsEntry("data", review);
    }

    @Test
    void aReviewFromAnUnknownUserIsRejected() {
        when(userMapper.selectByUsername("buyer7")).thenReturn(null);

        ResponseEntity<?> response = reviewController.createReview(new ReviewRequest(), buyer7);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(body(response)).containsEntry("message", "User not found");
        verifyNoInteractions(reviewService);
    }

    @Test
    void aReviewTheServiceRejectsIsA400WithItsReason() {
        User me = new User();
        me.setId(7L);
        when(userMapper.selectByUsername("buyer7")).thenReturn(me);
        when(reviewService.createReview(any(), any())).thenThrow(new RuntimeException("This order has already been reviewed"));

        ResponseEntity<?> response = reviewController.createReview(new ReviewRequest(), buyer7);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(body(response)).containsEntry("success", false)
                .containsEntry("message", "This order has already been reviewed");
    }

    @Test
    void theCheckSaysWhetherAnOrderWasReviewed() {
        when(reviewService.hasReviewed(30)).thenReturn(true);
        when(reviewService.hasReviewed(31)).thenThrow(new RuntimeException("db down"));

        assertThat(body(reviewController.checkReview(30))).containsEntry("hasReviewed", true);
        assertThat(reviewController.checkReview(31).getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void productReviewsComeWithTheirAverageAndCount() {
        List<ReviewResponse> reviews = List.of(new ReviewResponse(), new ReviewResponse());
        when(reviewService.getProductReviews(42L)).thenReturn(reviews);
        when(reviewService.getProductAverageRating(42L)).thenReturn(4.5);
        when(reviewService.getProductReviews(43L)).thenThrow(new RuntimeException("db down"));

        assertThat(body(reviewController.getProductReviews(42L)))
                .containsEntry("reviews", reviews)
                .containsEntry("averageRating", 4.5)
                .containsEntry("totalReviews", 2);
        assertThat(reviewController.getProductReviews(43L).getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void sellerAndBuyerReviewsAreListed() {
        List<ReviewResponse> reviews = List.of(new ReviewResponse());
        when(reviewService.getSellerReviews(9)).thenReturn(reviews);
        when(reviewService.getBuyerReviews(7)).thenReturn(reviews);
        when(reviewService.getSellerReviews(8)).thenThrow(new RuntimeException("db down"));
        when(reviewService.getBuyerReviews(8)).thenThrow(new RuntimeException("db down"));

        assertThat(body(reviewController.getSellerReviews(9))).containsEntry("reviews", reviews);
        assertThat(body(reviewController.getBuyerReviews(7))).containsEntry("reviews", reviews);
        assertThat(reviewController.getSellerReviews(8).getStatusCode().value()).isEqualTo(400);
        assertThat(reviewController.getBuyerReviews(8).getStatusCode().value()).isEqualTo(400);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> body(ResponseEntity<?> response) {
        return (Map<String, Object>) response.getBody();
    }
}
