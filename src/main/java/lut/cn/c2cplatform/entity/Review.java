package lut.cn.c2cplatform.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.Instant;

@Data
public class Review implements Serializable {
    private Long id;
    private Integer orderId;
    private Long productId;
    private Integer buyerId;
    private Integer sellerId;
    private Integer productRating; // Product rating 1-5
    private Integer sellerRating; // Seller rating 1-5
    private String comment; // Review text
    private String reviewImages; // Review images, comma-separated
    private Boolean isAnonymous; // Whether the review is anonymous
    private Instant createdAt;
    private Instant updatedAt;
}

