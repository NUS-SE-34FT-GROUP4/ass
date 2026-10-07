package lut.cn.c2cplatform.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.Instant;

@Data
public class CreditScore implements Serializable {
    private Long id;
    private Long userId;
    private Integer totalScore; // Total credit score
    private Integer level; // Credit level 1-5
    private Integer totalSales; // Total sales
    private Integer totalPurchases; // Total purchases
    private Double averageSellerRating; // Average seller rating
    private Integer positiveReviews; // Positive reviews
    private Integer neutralReviews; // Neutral reviews
    private Integer negativeReviews; // Negative reviews
    private Instant updatedAt;
}

