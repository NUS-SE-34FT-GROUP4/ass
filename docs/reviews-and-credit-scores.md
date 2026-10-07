# Reviews and Credit Scores

## Overview

The system implements full reviews and credit scores, with these core features:

### 1. Reviews
- After completing an order, the buyer can review the product and the seller
- 1-5 star ratings (separate product and seller ratings)
- Text reviews and image uploads (several images)
- An anonymous review option
- The product page shows every review, with review images
- Each order can be reviewed only once
- The seller's credit score is updated automatically after a review
- **Fix note**: fixed a database error caused by an empty `updated_at` when creating a review; creating a review now sets the `createdAt` and `updatedAt` timestamps automatically

### 2. Credit scores
- Credit score formula: base 100 + completed trades × 2 + average rating × 20 - negative reviews × 10
- Five credit levels:
  - Level 1: Newcomer (score < 150 or trades < 5) 🌱
  - Level 2: Bronze Seller (score ≥ 150 and trades ≥ 5) 🥉
  - Level 3: Silver Seller (score ≥ 250 and trades ≥ 20 and average rating ≥ 3.5) 🥈
  - Level 4: Gold Seller (score ≥ 400 and trades ≥ 50 and average rating ≥ 4.0) 🥇
  - Level 5: Diamond Seller (score ≥ 600 and trades ≥ 100 and average rating ≥ 4.5) 💎
- Automatic statistics: items sold, items bought, positive rate, review breakdown and more

## Database schema

### review table
```sql
CREATE TABLE `review` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Review ID',
    `order_id` INT NOT NULL COMMENT 'Order ID',
    `product_id` BIGINT NOT NULL COMMENT 'Product ID',
    `buyer_id` INT NOT NULL COMMENT 'Buyer ID',
    `seller_id` INT NOT NULL COMMENT 'Seller ID',
    `product_rating` INT NOT NULL COMMENT 'Product rating 1-5',
    `seller_rating` INT NOT NULL COMMENT 'Seller rating 1-5',
    `comment` TEXT NULL COMMENT 'Review text',
    `review_images` TEXT NULL COMMENT 'Review images, comma-separated',
    `is_anonymous` BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Whether the review is anonymous',
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_order_id` (`order_id`)
);
```

### credit_score table
```sql
CREATE TABLE `credit_score` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Credit score ID',
    `user_id` BIGINT NOT NULL COMMENT 'User ID',
    `total_score` INT NOT NULL DEFAULT 100 COMMENT 'Total credit score',
    `level` INT NOT NULL DEFAULT 1 COMMENT 'Credit level 1-5',
    `total_sales` INT NOT NULL DEFAULT 0 COMMENT 'Total sales',
    `total_purchases` INT NOT NULL DEFAULT 0 COMMENT 'Total purchases',
    `average_seller_rating` DOUBLE NOT NULL DEFAULT 0.0 COMMENT 'Average seller rating',
    `positive_reviews` INT NOT NULL DEFAULT 0 COMMENT 'Positive reviews',
    `neutral_reviews` INT NOT NULL DEFAULT 0 COMMENT 'Neutral reviews',
    `negative_reviews` INT NOT NULL DEFAULT 0 COMMENT 'Negative reviews',
    `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_user_id` (`user_id`)
);
```

## Backend API

### Review endpoints

#### 1. Create a review
```
POST /api/reviews
Authorization: Bearer {token}

Request Body:
{
  "orderId": 1,
  "productRating": 5,      // Product rating 1-5
  "sellerRating": 5,       // Seller rating 1-5
  "comment": "Great item", // Review text (optional)
  "reviewImages": [],      // Review image URLs (optional)
  "isAnonymous": false     // Whether anonymous (optional)
}

Response:
{
  "success": true,
  "message": "Review submitted",
  "data": { ... }
}
```

#### 2. Check whether an order has been reviewed
```
GET /api/reviews/check/{orderId}

Response:
{
  "success": true,
  "hasReviewed": false
}
```

#### 3. Get all reviews for a product
```
GET /api/reviews/product/{productId}

Response:
{
  "success": true,
  "reviews": [ ... ],
  "averageRating": 4.5,
  "totalReviews": 10
}
```

#### 4. Get all reviews for a seller
```
GET /api/reviews/seller/{sellerId}

Response:
{
  "success": true,
  "reviews": [ ... ]
}
```

### Credit score endpoints

#### 1. Get a user's credit score
```
GET /api/credit-score/{userId}

Response:
{
  "success": true,
  "data": {
    "userId": 1,
    "username": "user123",
    "displayName": "username",
    "avatarUrl": "...",
    "totalScore": 150,
    "level": 2,
    "levelName": "Bronze Seller",
    "totalSales": 5,
    "totalPurchases": 3,
    "averageSellerRating": 4.5,
    "positiveReviews": 4,
    "neutralReviews": 1,
    "negativeReviews": 0,
    "totalReviews": 5,
    "positiveRate": 80.0
  }
}
```

#### 2. Update a credit score (administrator or system)
```
POST /api/credit-score/update/{userId}
Authorization: Bearer {token}

Response:
{
  "success": true,
  "message": "Credit score updated",
  "data": { ... }
}
```

## Frontend components

### 1. ReviewView.vue
The review page, where users review an order after confirming receipt.

Features:
- Product rating (1-5 stars)
- Seller rating (1-5 stars)
- Text review
- Anonymous option
- Submit the review

Route: `/review/:orderId`

### 2. ProductReviews.vue
The product review component, shown on the product page.

Features:
- Average rating
- List of every review
- Buyer details (or anonymous)
- Review time, rating and text

### 3. CreditScoreCard.vue
The credit score card, shown in the seller section of the product page.

Features:
- Seller credit level and icon
- Total credit score
- Items sold and bought
- Positive rate
- Review breakdown (positive/neutral/negative)
- Average seller rating

## Flow

### Review flow
1. The buyer buys and pays for an item
2. The seller ships it
3. The buyer confirms receipt (order status becomes DELIVERED)
4. The "Order Center" shows a "⭐ Review Order" button
5. Clicking it opens the review page
6. The buyer fills in the ratings and review
7. The buyer submits the review
8. The system updates the seller's credit score automatically
9. The button changes to "✅ Reviewed" (no second review)

### When credit scores update
1. When the buyer confirms receipt (both buyer and seller scores update automatically)
2. After the buyer submits a review (the seller's score updates automatically)
3. When an administrator triggers an update manually

### On the product page
1. The seller credit card (credit level, rating and so on)
2. The product review list (every review of the product)

## Implementation

### Backend classes

1. **Entities**
   - `Review.java` - review entity
   - `CreditScore.java` - credit score entity

2. **Mappers**
   - `ReviewMapper.java` - review data access
   - `CreditScoreMapper.java` - credit score data access

3. **Services**
   - `ReviewService.java` / `ReviewServiceImpl.java` - review business logic
   - `CreditScoreService.java` / `CreditScoreServiceImpl.java` - credit score business logic

4. **Controllers**
   - `ReviewController.java` - review API
   - `CreditScoreController.java` - credit score API

5. **DTOs**
   - `ReviewRequest.java` - review request
   - `ReviewResponse.java` - review response
   - `CreditScoreResponse.java` - credit score response

### Frontend files

1. **Views**
   - `ReviewView.vue` - review page

2. **Components**
   - `ProductReviews.vue` - product review component
   - `CreditScoreCard.vue` - credit score card component

3. **Router**
   - Added the `/review/:orderId` route

4. **Updated files**
   - `ProductDetail.vue` - shows the seller credit score and product reviews
   - `OrderHistory.vue` - adds the review button and related logic

## Notes

1. **Review rules**
   - Only orders with status DELIVERED can be reviewed
   - Each order can be reviewed only once
   - Ratings must be between 1 and 5
   - Positive: 4-5 stars, neutral: 3 stars, negative: 1-2 stars

2. **Credit score calculation**
   - New users start with 100 points
   - Completing trades raises the score
   - Negative reviews lower the score
   - The credit level combines total score, number of trades and average rating

3. **Data consistency**
   - Credit scores update automatically when an order is confirmed as received
   - Credit scores update automatically when a review is submitted
   - Transactions keep the data consistent

## Testing tips

1. Test reviews
   - Create an order -> pay -> confirm receipt -> review
   - Try reviewing twice (should be blocked)
   - Test anonymous reviews
   - Test how different ratings affect the credit score

2. Test credit scores
   - Check a new user's starting score
   - Check how the score changes after several trades
   - Test the promotion conditions for each level
   - Test how negative reviews affect the score

3. Test the frontend
   - Does the product page show the seller's credit score correctly?
   - Does the product page show the review list correctly?
   - Does the order center show the review button correctly?
   - Does the review page work fully?

## Files delivered

### Backend files (Java)
✅ `/src/main/java/lut/cn/c2cplatform/entity/Review.java`
✅ `/src/main/java/lut/cn/c2cplatform/entity/CreditScore.java`
✅ `/src/main/java/lut/cn/c2cplatform/mapper/ReviewMapper.java`
✅ `/src/main/java/lut/cn/c2cplatform/mapper/CreditScoreMapper.java`
✅ `/src/main/java/lut/cn/c2cplatform/mapper/OrderMapper.java` (fixed)
✅ `/src/main/java/lut/cn/c2cplatform/dto/ReviewRequest.java`
✅ `/src/main/java/lut/cn/c2cplatform/dto/ReviewResponse.java`
✅ `/src/main/java/lut/cn/c2cplatform/dto/CreditScoreResponse.java`
✅ `/src/main/java/lut/cn/c2cplatform/service/ReviewService.java`
✅ `/src/main/java/lut/cn/c2cplatform/service/CreditScoreService.java`
✅ `/src/main/java/lut/cn/c2cplatform/service/impl/ReviewServiceImpl.java`
✅ `/src/main/java/lut/cn/c2cplatform/service/impl/CreditScoreServiceImpl.java`
✅ `/src/main/java/lut/cn/c2cplatform/service/impl/OrderServiceImpl.java` (updated)
✅ `/src/main/java/lut/cn/c2cplatform/controller/ReviewController.java`
✅ `/src/main/java/lut/cn/c2cplatform/controller/CreditScoreController.java`

### Frontend files (Vue)
✅ `/frontend/src/views/ReviewView.vue`
✅ `/frontend/src/components/ProductReviews.vue`
✅ `/frontend/src/components/CreditScoreCard.vue`
✅ `/frontend/src/views/ProductDetail.vue` (updated)
✅ `/frontend/src/views/OrderHistory.vue` (updated)
✅ `/frontend/src/router/index.js` (updated)

### Database files
✅ `/init.sql` (added the review and credit_score tables)

## Summary

The review and credit score system is fully implemented, including:
- ✅ Full review features (create, query, display)
- ✅ Credit scores (automatic calculation and levels)
- ✅ Frontend components (review page, review list, credit score card)
- ✅ Database schema
- ✅ Complete API
- ✅ Automatic updates

The design follows mainstream second-hand marketplaces and provides a complete user credit and review system.

