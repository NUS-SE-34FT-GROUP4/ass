# Orders and Payments

## Overview

The order and payment system is the core of the C2C second-hand trading platform, managing the whole flow from placing an order and paying to completing the trade. It is built on a distributed architecture with Redis caching and RabbitMQ messaging, supports bulk checkout from the cart or direct purchase from a product page, and provides simulated payment, payment password management and order history.

### Core features

#### 1. Creating and managing orders
- **Several ways to order**: bulk checkout from the cart or direct purchase from the product page
- **Split by seller**: when cart items come from different sellers, the system creates a sub-order for each
- **Stock checks**: stock is checked in real time when ordering to prevent overselling
- **Order snapshot**: the product details at order time (price, title, images) are recorded so later product edits do not change past orders
- **Shipping addresses**: users keep several shipping addresses and choose one when ordering

#### 2. Payments
- **Simulated payment flow**: a realistic simulation including payment password check, balance deduction and payment result
- **Payment password**: a 6-digit payment password must be set before the first payment; it can be changed and reset
- **Balance**: account balance management with top-up, spending and refunds
- **Payment methods**: Alipay, WeChat Pay, bank card and balance (simulated)
- **Transaction safety**: Redis distributed locks prevent duplicate payments and keep transactions consistent

#### 3. Order center (three tabs)
- **Bought**:
  - Every order placed as a buyer
  - Pay, cancel, confirm receipt and review
  - Shows order status, shipping information, payment method and other details
  - Filter by status (awaiting payment, paid, shipped, completed)
  
- **Sold**:
  - Every order received as a seller
  - Sales and order status in real time
  - Ship orders and view buyer details
  - Sales and revenue statistics
  
- **Bargains**:
  - Every bargain the user takes part in
  - Bargain progress and help records
  - Order directly after a successful bargain
  - Live bargain status (in progress, succeeded, failed, expired)

#### 4. Order status transitions
The system manages the full order lifecycle:
- **PENDING**: the order is created and waiting for the buyer to pay
- **PAID**: the buyer has paid and is waiting for the seller to ship
- **SHIPPED**: the seller has shipped and the item is in transit
- **DELIVERED**: the item has arrived and is waiting for the buyer to confirm receipt
- **COMPLETED**: the buyer has confirmed receipt and the trade is complete
- **CANCELLED**: the order was cancelled (only unpaid orders can be cancelled)
- **REFUNDED**: the order was refunded

#### 5. Notifications
- **Order created**: pushed to buyer and seller in real time over WebSocket
- **Payment succeeded**: both sides are notified as soon as payment completes
- **Shipping reminder**: the buyer is notified when the seller ships
- **Receipt reminder**: the buyer is reminded to confirm receipt
- **Review reminder**: the buyer is reminded to leave a review after the trade

#### 6. Distributed features
- **Redis cache**: hot order data is cached for faster queries
- **Distributed locks**: prevent concurrent payments and overselling
- **Message queue**: order creation, payment and shipping events are processed asynchronously
- **Transactions**: Spring transactions keep order creation and stock deduction atomic

## Database schema

### order table
```sql
CREATE TABLE `order` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT 'Order ID',
  `user_id` int NOT NULL COMMENT 'User ID',
  `seller_id` int NOT NULL COMMENT 'Seller ID',
  `total_price` decimal(10,2) NOT NULL COMMENT 'Total price',
  `status` varchar(50) NOT NULL COMMENT 'Order status',
  `shipping_address` varchar(255) DEFAULT NULL COMMENT 'Shipping address',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `seller_id` (`seller_id`),
  CONSTRAINT `order_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
  CONSTRAINT `order_ibfk_2` FOREIGN KEY (`seller_id`) REFERENCES `user` (`id`)
);
```

### order_item table
```sql
CREATE TABLE `order_item` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT 'Order item ID',
  `order_id` int NOT NULL COMMENT 'Order ID',
  `product_id` int NOT NULL COMMENT 'Product ID',
  `quantity` int NOT NULL COMMENT 'Quantity',
  `price` decimal(10,2) NOT NULL COMMENT 'Unit price',
  PRIMARY KEY (`id`),
  KEY `order_id` (`order_id`),
  KEY `product_id` (`product_id`),
  CONSTRAINT `order_item_ibfk_1` FOREIGN KEY (`order_id`) REFERENCES `order` (`id`),
  CONSTRAINT `order_item_ibfk_2` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`)
);
```

## Backend API

#### 1. Create an order
`POST /api/orders`
- **Authorization**: `Bearer {token}`
- **Request Body**: `{ "productItems": [{ "productId": 1, "quantity": 2 }], "shippingAddress": "..." }`
- **Response**: `{ "success": true, "orderIds": [101, 102], "totalAmount": 199.8 }`

#### 2. Pay for an order
`POST /api/orders/{orderId}/pay`
- **Authorization**: `Bearer {token}`
- **Request Body**: `{ "paymentPassword": "123456" }`
- **Response**: a success or failure message.

#### 3. List the buyer's orders
`GET /api/orders`
- **Authorization**: `Bearer {token}`
- **Response**: every order the current user placed as a buyer.

#### 4. List the seller's orders
`GET /api/orders/seller`
- **Authorization**: `Bearer {token}`
- **Response**: every order the current user received as a seller (buyers purchasing products this user listed).

#### 5. Get a single order
`GET /api/orders/{orderId}`
- **Authorization**: `Bearer {token}`
- **Response**: the full order with the given ID, including its items.

#### 6. Confirm receipt
`POST /api/orders/{orderId}/confirm`
- **Authorization**: `Bearer {token}`
- **Response**: a success or failure message.

#### 7. Cancel an order
`POST /api/orders/{orderId}/cancel`
- **Authorization**: `Bearer {token}`
- **Response**: a success or failure message.

#### 8. Set or change the payment password
`POST /api/users/set-payment-password`
- **Authorization**: `Bearer {token}`
- **Request Body**: `{ "password": "new-payment-password" }`
- **Response**: a success or failure message.

## Frontend views and components

- **PaymentView.vue**: the payment page, for confirming the order and paying.
  - Route: `/payment`
  - Reads the items to check out from the route parameters or the store and shows the order total and shipping address.
  - Includes a modal for entering the payment password.

- **OrderHistory.vue**: the order center page.
  - Route: `/order-history`
  - Has three tabs:
    - **📦 Bought**: orders placed as a buyer, with actions depending on the order status (pay, cancel, confirm receipt, review)
    - **💰 Sold**: orders received as a seller, with buyer details and order status (waiting for the buyer to pay, waiting for the buyer to confirm receipt, completed)
    - **🔪 Bargains**: every bargain the user takes part in, with progress, number of helpers and time left
  - Detects expired orders and marks unpaid orders past their deadline as "Expired".

- **PaymentPasswordSetup.vue**: the payment password setup page.
  - Route: `/setup-payment-password`
  - Users who try to pay without a payment password are sent here.

- **PaymentResult.vue**: the payment result page.
  - Route: `/payment-result`
  - Shows whether payment succeeded or failed.

## Order creation and payment flow

1.  **Start**: the user clicks "Checkout" in `CartView.vue` or "Buy Now" in `ProductDetail.vue`.
2.  **Go to the payment page**: the frontend passes the items to check out (a list of `productId` and `quantity`) to `PaymentView.vue` through route parameters or the store.
3.  **Create the order**: when `PaymentView.vue` loads or the user clicks "Place Order", it calls `POST /api/orders`.
4.  **Backend processing**: `OrderService.java` receives the request and:
    -   Groups the items by `sellerId`.
    -   Creates an order (`order` table) for each seller.
    -   Creates order items (`order_item` table) for each order's products.
    -   Locks or decreases the product stock.
    -   Removes the items from the cart.
    -   Runs everything in a single database transaction.
    -   Returns the IDs of the new orders and the total amount.
5.  **Pay**: the user enters the payment password on the payment page and clicks "Confirm Payment".
6.  **Call the payment API**: the frontend calls `POST /api/orders/{orderId}/pay` (for several orders it loops, or a bulk payment endpoint can be provided).
7.  **Backend verification and processing**: `PaymentService.java` or `OrderService.java`:
    -   Verifies the payment password.
    -   Checks the user's balance is sufficient.
    -   Deducts the balance.
    -   Updates the order status to `PROCESSING` (awaiting shipment).
    -   (In a real system this is where a third-party payment gateway would be called.)
8.  **Payment complete**: when the API returns success, the frontend goes to `PaymentResult.vue`, shows the success message and offers to view the order or return home.

## Order state machine

-   **PENDING_PAYMENT**: awaiting payment. The order is created and waiting for the user to pay.
-   **PROCESSING**: processing / awaiting shipment. The user has paid and the seller has not shipped yet.
-   **SHIPPED**: shipped. The seller has shipped and the buyer has not received it yet.
-   **DELIVERED**: delivered / awaiting confirmation. The buyer has the item but has not confirmed receipt.
-   **COMPLETED**: completed. The buyer has confirmed receipt and the trade is over.
-   **CANCELLED**: cancelled. The user cancelled before paying, or payment timed out.
-   **REFUNDED**: refunded. A refund took place.
