package sg.edu.nus.iss.c2csectrade.service;

import sg.edu.nus.iss.c2csectrade.dto.ReviewRequest;
import sg.edu.nus.iss.c2csectrade.dto.ReviewResponse;
import sg.edu.nus.iss.c2csectrade.entity.Order;
import sg.edu.nus.iss.c2csectrade.entity.OrderItem;
import sg.edu.nus.iss.c2csectrade.entity.Product;
import sg.edu.nus.iss.c2csectrade.entity.Review;
import sg.edu.nus.iss.c2csectrade.entity.User;
import sg.edu.nus.iss.c2csectrade.mapper.OrderItemMapper;
import sg.edu.nus.iss.c2csectrade.mapper.OrderMapper;
import sg.edu.nus.iss.c2csectrade.mapper.ProductMapper;
import sg.edu.nus.iss.c2csectrade.mapper.ReviewMapper;
import sg.edu.nus.iss.c2csectrade.mapper.UserMapper;
import sg.edu.nus.iss.c2csectrade.service.impl.ReviewServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Only the buyer of a delivered or completed order can review it, once.
 * Both ratings are 1 to 5, and a review recomputes the seller's credit score.
 * Buyer 7 reviews order 30 for product 42, sold by seller 9.
 */
@ExtendWith(MockitoExtension.class)
class ReviewServiceImplTest {

    @Mock
    private ReviewMapper reviewMapper;
    @Mock
    private OrderMapper orderMapper;
    @Mock
    private OrderItemMapper orderItemMapper;
    @Mock
    private ProductMapper productMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private CreditScoreService creditScoreService;
    @Mock
    private HybridRecommendationService hybridRecommendationService;

    @InjectMocks
    private ReviewServiceImpl reviewService;

    @Test
    void theBuyerReviewsACompletedOrderAndTheSellerIsRescored() {
        when(orderMapper.findById(30)).thenReturn(order("COMPLETED"));
        when(orderItemMapper.findByOrderId(30)).thenReturn(List.of(item()));
        when(productMapper.selectById(42L)).thenReturn(product());

        Review review = reviewService.createReview(7, request(5, 4));

        assertThat(review.getBuyerId()).isEqualTo(7);
        assertThat(review.getSellerId()).isEqualTo(9);
        assertThat(review.getProductId()).isEqualTo(42L);
        assertThat(review.getIsAnonymous()).isFalse();
        assertThat(review.getReviewImages()).isNull();
        verify(reviewMapper).insert(review);
        verify(hybridRecommendationService).updatePopularity(42L, "review");
        verify(creditScoreService).updateCreditScore(9L);
    }

    @Test
    void aDeliveredOrderCanBeReviewedAnonymouslyWithImages() {
        when(orderMapper.findById(30)).thenReturn(order("DELIVERED"));
        when(orderItemMapper.findByOrderId(30)).thenReturn(List.of(item()));
        when(productMapper.selectById(42L)).thenReturn(product());
        ReviewRequest request = request(3, 3);
        request.setIsAnonymous(true);
        request.setReviewImages(List.of("a.png", "b.png"));

        Review review = reviewService.createReview(7, request);

        assertThat(review.getIsAnonymous()).isTrue();
        assertThat(review.getReviewImages()).isEqualTo("a.png,b.png");
    }

    @Test
    void aMissingOrderCannotBeReviewed() {
        when(orderMapper.findById(30)).thenReturn(null);

        assertThatThrownBy(() -> reviewService.createReview(7, request(5, 5))).hasMessageContaining("Order not found");
    }

    @Test
    void onlyTheBuyerCanReview() {
        when(orderMapper.findById(30)).thenReturn(order("COMPLETED"));

        assertThatThrownBy(() -> reviewService.createReview(8, request(5, 5))).hasMessageContaining("not allowed");
    }

    @Test
    void anOrderThatIsNotDeliveredCannotBeReviewed() {
        when(orderMapper.findById(30)).thenReturn(order("PAID"));

        assertThatThrownBy(() -> reviewService.createReview(7, request(5, 5))).hasMessageContaining("not completed");
    }

    @Test
    void anOrderIsReviewedOnlyOnce() {
        when(orderMapper.findById(30)).thenReturn(order("COMPLETED"));
        when(reviewMapper.selectByOrderId(30)).thenReturn(new Review());

        assertThatThrownBy(() -> reviewService.createReview(7, request(5, 5))).hasMessageContaining("already been reviewed");
        verify(reviewMapper, never()).insert(any());
    }

    @ParameterizedTest
    @CsvSource({"0, 5, Product rating", "6, 5, Product rating", "5, 0, Seller rating", "5, 6, Seller rating"})
    void ratingsMustBeOneToFive(int productRating, int sellerRating, String message) {
        when(orderMapper.findById(30)).thenReturn(order("COMPLETED"));

        assertThatThrownBy(() -> reviewService.createReview(7, request(productRating, sellerRating)))
                .hasMessageContaining(message);
    }

    @Test
    void anOrderWithoutItemsOrProductCannotBeReviewed() {
        when(orderMapper.findById(30)).thenReturn(order("COMPLETED"));
        when(orderItemMapper.findByOrderId(30)).thenReturn(List.of());
        assertThatThrownBy(() -> reviewService.createReview(7, request(5, 5))).hasMessageContaining("Order item not found");

        when(orderItemMapper.findByOrderId(30)).thenReturn(List.of(item()));
        when(productMapper.selectById(42L)).thenReturn(null);
        assertThatThrownBy(() -> reviewService.createReview(7, request(5, 5))).hasMessageContaining("Product not found");
        verify(creditScoreService, never()).updateCreditScore(any());
    }

    @Test
    void hasReviewedLooksUpTheOrder() {
        when(reviewMapper.selectByOrderId(30)).thenReturn(new Review());
        when(reviewMapper.selectByOrderId(31)).thenReturn(null);

        assertThat(reviewService.hasReviewed(30)).isTrue();
        assertThat(reviewService.hasReviewed(31)).isFalse();
    }

    @Test
    void aNamedReviewShowsTheBuyerAndItsImages() {
        Review review = stored(false, "a.png,b.png");
        when(reviewMapper.selectByProductId(42L)).thenReturn(List.of(review));
        when(productMapper.selectById(42L)).thenReturn(product());
        User buyer = new User();
        buyer.setDisplayName("Buyer Seven");
        buyer.setAvatarUrl("https://cdn.example/b7.png");
        when(userMapper.selectById(7L)).thenReturn(buyer);

        ReviewResponse response = reviewService.getProductReviews(42L).get(0);

        assertThat(response.getProductName()).isEqualTo("Desk lamp");
        assertThat(response.getBuyerId()).isEqualTo(7);
        assertThat(response.getBuyerName()).isEqualTo("Buyer Seven");
        assertThat(response.getReviewImages()).containsExactly("a.png", "b.png");
    }

    @Test
    void anAnonymousReviewHidesTheBuyer() {
        when(reviewMapper.selectBySellerId(9)).thenReturn(List.of(stored(true, null)));
        when(productMapper.selectById(42L)).thenReturn(null);

        ReviewResponse response = reviewService.getSellerReviews(9).get(0);

        assertThat(response.getBuyerId()).isNull();
        assertThat(response.getBuyerName()).isEqualTo("Anonymous");
        assertThat(response.getProductName()).isNull();
        assertThat(response.getReviewImages()).isEmpty();
        verify(userMapper, never()).selectById(any());
    }

    @Test
    void aReviewByADeletedBuyerHasNoName() {
        when(reviewMapper.selectByBuyerId(7)).thenReturn(List.of(stored(false, "")));
        when(productMapper.selectById(42L)).thenReturn(product());
        when(userMapper.selectById(7L)).thenReturn(null);

        ReviewResponse response = reviewService.getBuyerReviews(7).get(0);

        assertThat(response.getBuyerName()).isNull();
        assertThat(response.getReviewImages()).isEmpty();
    }

    @Test
    void anUnratedProductAveragesZero() {
        when(reviewMapper.getAverageProductRating(42L)).thenReturn(4.5);
        when(reviewMapper.getAverageProductRating(43L)).thenReturn(null);

        assertThat(reviewService.getProductAverageRating(42L)).isEqualTo(4.5);
        assertThat(reviewService.getProductAverageRating(43L)).isZero();
    }

    private static ReviewRequest request(int productRating, int sellerRating) {
        ReviewRequest request = new ReviewRequest();
        request.setOrderId(30);
        request.setProductRating(productRating);
        request.setSellerRating(sellerRating);
        request.setComment("Works well");
        return request;
    }

    private static Order order(String status) {
        Order order = new Order();
        order.setId(30);
        order.setUserId(7);
        order.setStatus(status);
        return order;
    }

    private static OrderItem item() {
        OrderItem item = new OrderItem();
        item.setProductId(42);
        return item;
    }

    private static Product product() {
        Product product = new Product();
        product.setId(42L);
        product.setUserId(9L);
        product.setName("Desk lamp");
        return product;
    }

    private static Review stored(boolean anonymous, String images) {
        Review review = new Review();
        review.setId(1L);
        review.setOrderId(30);
        review.setProductId(42L);
        review.setBuyerId(7);
        review.setSellerId(9);
        review.setProductRating(5);
        review.setSellerRating(4);
        review.setIsAnonymous(anonymous);
        review.setReviewImages(images);
        return review;
    }
}
