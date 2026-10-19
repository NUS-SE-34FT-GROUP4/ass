package sg.edu.nus.iss.c2csectrade.service;

import sg.edu.nus.iss.c2csectrade.dto.CreditScoreResponse;
import sg.edu.nus.iss.c2csectrade.entity.CreditScore;
import sg.edu.nus.iss.c2csectrade.entity.User;
import sg.edu.nus.iss.c2csectrade.mapper.CreditScoreMapper;
import sg.edu.nus.iss.c2csectrade.mapper.OrderMapper;
import sg.edu.nus.iss.c2csectrade.mapper.ReviewMapper;
import sg.edu.nus.iss.c2csectrade.mapper.UserMapper;
import sg.edu.nus.iss.c2csectrade.service.impl.CreditScoreServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Confirming receipt recomputes both parties' credit scores:
 * 100 + 2 per completed trade + 20 x average rating - 10 per negative review,
 * never below zero. The level also needs enough trades and a good enough rating.
 * The user is 9.
 */
@ExtendWith(MockitoExtension.class)
class CreditScoreServiceImplTest {

    @Mock
    private CreditScoreMapper creditScoreMapper;
    @Mock
    private ReviewMapper reviewMapper;
    @Mock
    private OrderMapper orderMapper;
    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private CreditScoreServiceImpl creditScoreService;

    @Test
    void aNewUserStartsAt100AsANewcomer() {
        when(creditScoreMapper.existsByUserId(9L)).thenReturn(0);

        creditScoreService.initializeCreditScore(9L);

        ArgumentCaptor<CreditScore> created = ArgumentCaptor.forClass(CreditScore.class);
        verify(creditScoreMapper).insert(created.capture());
        assertThat(created.getValue().getTotalScore()).isEqualTo(100);
        assertThat(created.getValue().getLevel()).isEqualTo(1);
        assertThat(created.getValue().getTotalSales()).isZero();
    }

    @Test
    void anExistingScoreIsNotCreatedAgain() {
        when(creditScoreMapper.existsByUserId(9L)).thenReturn(1);

        creditScoreService.initializeCreditScore(9L);

        verify(creditScoreMapper, never()).insert(any());
    }

    @Test
    void theScoreCountsTradesAndRatingAndPenalisesNegativeReviews() {
        CreditScore score = score();
        when(creditScoreMapper.selectByUserId(9L)).thenReturn(score);
        stats(3, 2, 4.0, 4, 1, 1);

        creditScoreService.updateCreditScore(9L);

        // 100 + 5 trades x 2 + 4.0 x 20 - 1 negative x 10
        assertThat(score.getTotalScore()).isEqualTo(180);
        assertThat(score.getLevel()).isEqualTo(2);
        assertThat(score.getPositiveReviews()).isEqualTo(4);
        verify(creditScoreMapper).update(score);
    }

    @ParameterizedTest(name = "{0} trades, rating {1}, {2} negative -> score {3}, level {4}")
    @CsvSource({
            "230, 4.5,  0, 650, 5",
            "120, 4.0,  0, 420, 4",
            "60,  3.5,  0, 290, 3",
            "20,  2.0,  0, 180, 2",
            "4,   5.0,  0, 208, 1",
            "0,   0.0, 50,   0, 1"
    })
    void theLevelNeedsScoreTradesAndRatingTogether(int trades, double rating, int negatives, int expectedScore,
            int expectedLevel) {
        CreditScore score = score();
        when(creditScoreMapper.selectByUserId(9L)).thenReturn(score);
        stats(trades, 0, rating, 0, 0, negatives);

        creditScoreService.updateCreditScore(9L);

        assertThat(score.getTotalScore()).isEqualTo(expectedScore);
        assertThat(score.getLevel()).isEqualTo(expectedLevel);
    }

    @Test
    void aUserWithoutAScoreGetsOneBeforeItIsUpdated() {
        CreditScore score = score();
        when(creditScoreMapper.selectByUserId(9L)).thenReturn(null).thenReturn(score);
        when(creditScoreMapper.existsByUserId(9L)).thenReturn(0);
        stats(1, 0, null, 0, 0, 0);

        creditScoreService.updateCreditScore(9L);

        verify(creditScoreMapper).insert(any());
        // No rating yet counts as 0.0: 100 + 1 trade x 2
        assertThat(score.getAverageSellerRating()).isZero();
        assertThat(score.getTotalScore()).isEqualTo(102);
    }

    @Test
    void theProfileShowsTheUserAndThePositiveRate() {
        CreditScore score = score();
        score.setLevel(3);
        score.setPositiveReviews(3);
        score.setNeutralReviews(1);
        when(creditScoreMapper.selectByUserId(9L)).thenReturn(score);
        User user = new User();
        user.setUsername("seller9");
        user.setDisplayName("Seller Nine");
        when(userMapper.selectById(9L)).thenReturn(user);

        CreditScoreResponse response = creditScoreService.getUserCreditScore(9L);

        assertThat(response.getUsername()).isEqualTo("seller9");
        assertThat(response.getLevelName()).isEqualTo("Silver Seller");
        assertThat(response.getTotalReviews()).isEqualTo(4);
        assertThat(response.getPositiveRate()).isEqualTo(75.0);
    }

    @Test
    void aProfileWithNoReviewsHasAZeroPositiveRate() {
        when(creditScoreMapper.selectByUserId(9L)).thenReturn(null).thenReturn(score());
        when(creditScoreMapper.existsByUserId(9L)).thenReturn(0);
        when(userMapper.selectById(9L)).thenReturn(null);

        CreditScoreResponse response = creditScoreService.getUserCreditScore(9L);

        assertThat(response.getUsername()).isNull();
        assertThat(response.getLevelName()).isEqualTo("Newcomer");
        assertThat(response.getPositiveRate()).isZero();
    }

    @ParameterizedTest
    @CsvSource({"2, Bronze Seller", "4, Gold Seller", "5, Diamond Seller", "9, Unknown"})
    void eachLevelHasAName(int level, String name) {
        CreditScore score = score();
        score.setLevel(level);
        when(creditScoreMapper.selectByUserId(9L)).thenReturn(score);

        assertThat(creditScoreService.getUserCreditScore(9L).getLevelName()).isEqualTo(name);
    }

    private void stats(int sales, int purchases, Double rating, int positive, int neutral, int negative) {
        when(orderMapper.countCompletedOrdersBySeller(9)).thenReturn(sales);
        when(orderMapper.countCompletedOrdersByBuyer(9)).thenReturn(purchases);
        when(reviewMapper.getAverageSellerRating(9)).thenReturn(rating);
        when(reviewMapper.countPositiveReviews(9)).thenReturn(positive);
        when(reviewMapper.countNeutralReviews(9)).thenReturn(neutral);
        when(reviewMapper.countNegativeReviews(9)).thenReturn(negative);
    }

    private static CreditScore score() {
        CreditScore score = new CreditScore();
        score.setUserId(9L);
        score.setTotalScore(100);
        score.setLevel(1);
        score.setTotalSales(0);
        score.setTotalPurchases(0);
        score.setAverageSellerRating(0.0);
        score.setPositiveReviews(0);
        score.setNeutralReviews(0);
        score.setNegativeReviews(0);
        return score;
    }
}
