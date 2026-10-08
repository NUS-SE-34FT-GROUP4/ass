package sg.edu.nus.iss.c2csectrade.service.impl;

import sg.edu.nus.iss.c2csectrade.dto.CreditScoreResponse;
import sg.edu.nus.iss.c2csectrade.entity.CreditScore;
import sg.edu.nus.iss.c2csectrade.entity.User;
import sg.edu.nus.iss.c2csectrade.mapper.CreditScoreMapper;
import sg.edu.nus.iss.c2csectrade.mapper.OrderMapper;
import sg.edu.nus.iss.c2csectrade.mapper.ReviewMapper;
import sg.edu.nus.iss.c2csectrade.mapper.UserMapper;
import sg.edu.nus.iss.c2csectrade.service.CreditScoreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class CreditScoreServiceImpl implements CreditScoreService {

    @Autowired
    private CreditScoreMapper creditScoreMapper;

    @Autowired
    private ReviewMapper reviewMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private UserMapper userMapper;

    @Override
    public CreditScoreResponse getUserCreditScore(Long userId) {
        CreditScore creditScore = creditScoreMapper.selectByUserId(userId);

        // Initialise if missing
        if (creditScore == null) {
            initializeCreditScore(userId);
            creditScore = creditScoreMapper.selectByUserId(userId);
        }

        User user = userMapper.selectById(userId);

        CreditScoreResponse response = new CreditScoreResponse();
        response.setUserId(userId);
        if (user != null) {
            response.setUsername(user.getUsername());
            response.setDisplayName(user.getDisplayName());
            response.setAvatarUrl(user.getAvatarUrl());
        }

        response.setTotalScore(creditScore.getTotalScore());
        response.setLevel(creditScore.getLevel());
        response.setLevelName(getLevelName(creditScore.getLevel()));
        response.setTotalSales(creditScore.getTotalSales());
        response.setTotalPurchases(creditScore.getTotalPurchases());
        response.setAverageSellerRating(creditScore.getAverageSellerRating());
        response.setPositiveReviews(creditScore.getPositiveReviews());
        response.setNeutralReviews(creditScore.getNeutralReviews());
        response.setNegativeReviews(creditScore.getNegativeReviews());

        Integer totalReviews = creditScore.getPositiveReviews() +
                              creditScore.getNeutralReviews() +
                              creditScore.getNegativeReviews();
        response.setTotalReviews(totalReviews);

        // Positive review rate
        if (totalReviews > 0) {
            response.setPositiveRate((double) creditScore.getPositiveReviews() / totalReviews * 100);
        } else {
            response.setPositiveRate(0.0);
        }

        return response;
    }

    @Override
    @Transactional
    public void updateCreditScore(Long userId) {
        CreditScore creditScore = creditScoreMapper.selectByUserId(userId);

        if (creditScore == null) {
            initializeCreditScore(userId);
            creditScore = creditScoreMapper.selectByUserId(userId);
        }

        // Total sales (orders completed as seller)
        Integer totalSales = orderMapper.countCompletedOrdersBySeller(userId.intValue());

        // Total purchases (orders completed as buyer)
        Integer totalPurchases = orderMapper.countCompletedOrdersByBuyer(userId.intValue());

        // Average seller rating
        Double averageSellerRating = reviewMapper.getAverageSellerRating(userId.intValue());
        if (averageSellerRating == null) {
            averageSellerRating = 0.0;
        }

        // Positive, neutral and negative review counts
        Integer positiveReviews = reviewMapper.countPositiveReviews(userId.intValue());
        Integer neutralReviews = reviewMapper.countNeutralReviews(userId.intValue());
        Integer negativeReviews = reviewMapper.countNegativeReviews(userId.intValue());

        // Total credit score
        // Formula: base 100 + completed trades * 2 + average rating * 20 - negative reviews * 10
        int baseScore = 100;
        int transactionScore = (totalSales + totalPurchases) * 2;
        int ratingScore = (int) (averageSellerRating * 20);
        int penaltyScore = negativeReviews * 10;

        int totalScore = baseScore + transactionScore + ratingScore - penaltyScore;
        totalScore = Math.max(0, totalScore); // Never negative

        // Credit level (1-5)
        int level = calculateLevel(totalScore, totalSales + totalPurchases, averageSellerRating);

        // Update the credit score
        creditScore.setTotalScore(totalScore);
        creditScore.setLevel(level);
        creditScore.setTotalSales(totalSales);
        creditScore.setTotalPurchases(totalPurchases);
        creditScore.setAverageSellerRating(averageSellerRating);
        creditScore.setPositiveReviews(positiveReviews);
        creditScore.setNeutralReviews(neutralReviews);
        creditScore.setNegativeReviews(negativeReviews);
        creditScore.setUpdatedAt(Instant.now());

        creditScoreMapper.update(creditScore);
    }

    @Override
    @Transactional
    public void initializeCreditScore(Long userId) {
        int exists = creditScoreMapper.existsByUserId(userId);
        if (exists > 0) {
            return; // Already exists, nothing to initialise
        }

        CreditScore creditScore = new CreditScore();
        creditScore.setUserId(userId);
        creditScore.setTotalScore(100); // Starting score 100
        creditScore.setLevel(1); // Starting level 1
        creditScore.setTotalSales(0);
        creditScore.setTotalPurchases(0);
        creditScore.setAverageSellerRating(0.0);
        creditScore.setPositiveReviews(0);
        creditScore.setNeutralReviews(0);
        creditScore.setNegativeReviews(0);
        creditScore.setUpdatedAt(Instant.now());

        creditScoreMapper.insert(creditScore);
    }

    /**
     * Calculate the credit level
     * Level 1: Newcomer (score < 150 or trades < 5)
     * Level 2: Bronze (score >= 150 and trades >= 5)
     * Level 3: Silver (score >= 250 and trades >= 20 and average rating >= 3.5)
     * Level 4: Gold (score >= 400 and trades >= 50 and average rating >= 4.0)
     * Level 5: Diamond (score >= 600 and trades >= 100 and average rating >= 4.5)
     */
    private int calculateLevel(int totalScore, int totalTransactions, double avgRating) {
        if (totalScore >= 600 && totalTransactions >= 100 && avgRating >= 4.5) {
            return 5; // Diamond
        } else if (totalScore >= 400 && totalTransactions >= 50 && avgRating >= 4.0) {
            return 4; // Gold
        } else if (totalScore >= 250 && totalTransactions >= 20 && avgRating >= 3.5) {
            return 3; // Silver
        } else if (totalScore >= 150 && totalTransactions >= 5) {
            return 2; // Bronze
        } else {
            return 1; // Newcomer
        }
    }

    /**
     * Get the level name
     */
    private String getLevelName(int level) {
        switch (level) {
            case 1:
                return "Newcomer";
            case 2:
                return "Bronze Seller";
            case 3:
                return "Silver Seller";
            case 4:
                return "Gold Seller";
            case 5:
                return "Diamond Seller";
            default:
                return "Unknown";
        }
    }
}

