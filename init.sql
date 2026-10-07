-- =================================================================
-- c2csectrade - Complete Database Initialization Script
-- Version: 2.0
-- Last Updated: 2025-10-21
-- =================================================================
-- This script handles:
-- 1. Database creation with UTF-8mb4 character set.
-- 2. Dropping and recreating all tables for a clean slate.
-- 3. Seeding essential data (roles, admin user).
-- 4. Seeding realistic test data for users and products (books).
-- =================================================================

-- Set session variables for character encoding
SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- Create the database if it doesn't exist, ensuring correct character set
CREATE DATABASE IF NOT EXISTS trade CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE trade;

-- Disable foreign key checks to allow clean drop
SET FOREIGN_KEY_CHECKS = 0;

-- Drop tables in reverse order of dependency to avoid foreign key errors
DROP TABLE IF EXISTS `bargain_help`;
DROP TABLE IF EXISTS `bargain_activity`;
DROP TABLE IF EXISTS `shopping_cart`;
DROP TABLE IF EXISTS `user_favorites`;
DROP TABLE IF EXISTS `pms_product_media`;
DROP TABLE IF EXISTS `pms_report`;
DROP TABLE IF EXISTS `pms_product`;
DROP TABLE IF EXISTS `chat_message`;
DROP TABLE IF EXISTS `user_roles`;
DROP TABLE IF EXISTS `roles`;
DROP TABLE IF EXISTS `users`;

-- Re-enable foreign key checks
SET FOREIGN_KEY_CHECKS = 1;

-- =================================================================
-- Table Structure Definitions
-- =================================================================

-- Table `users`
CREATE TABLE `users` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'User unique ID',
    `username` VARCHAR(50) NOT NULL COMMENT 'Username, unique',
    `display_name` VARCHAR(100) NULL COMMENT 'Display name for the user',
    `password_hash` VARCHAR(255) NOT NULL COMMENT 'BCrypt hashed password',
    `payment_password_hash` VARCHAR(255) NULL COMMENT 'BCrypt hashed payment password (6 digits)',
    `email` VARCHAR(100) NOT NULL COMMENT 'User email, unique',
    `avatar_url` VARCHAR(255) NULL COMMENT 'User avatar URL',
    `balance` DECIMAL(15, 2) NOT NULL DEFAULT 0.00 COMMENT 'User balance',
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Last update time',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_username` (`username`),
    UNIQUE INDEX `uk_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Users table';

-- Table `roles`
CREATE TABLE `roles` (
    `id` INT NOT NULL AUTO_INCREMENT COMMENT 'Role unique ID',
    `name` VARCHAR(50) NOT NULL COMMENT 'Role name, unique',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_rolename` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Roles table';

-- Table `user_roles`
CREATE TABLE `user_roles` (
    `user_id` BIGINT NOT NULL COMMENT 'User ID',
    `role_id` INT NOT NULL COMMENT 'Role ID',
    PRIMARY KEY (`user_id`, `role_id`),
    INDEX `fk_user_roles_role_id_idx` (`role_id`),
    CONSTRAINT `fk_user_roles_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `fk_user_roles_role_id` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='User-role association table';

-- Table `chat_message`
CREATE TABLE `chat_message` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Message unique ID',
    `sender_id` BIGINT NULL COMMENT 'Sender user ID (NULL for system messages)',
    `recipient_id` BIGINT NOT NULL COMMENT 'Recipient user ID',
    `content` TEXT NOT NULL COMMENT 'Message content',
    `timestamp` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Message sent time',
    `is_read` BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Is the message read',
    `is_system_message` BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Is it a system message',
    PRIMARY KEY (`id`),
    INDEX `idx_sender_recipient` (`sender_id`, `recipient_id`),
    INDEX `idx_recipient_read` (`recipient_id`, `is_read`),
    CONSTRAINT `fk_chat_message_sender` FOREIGN KEY (`sender_id`) REFERENCES `users` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT `fk_chat_message_recipient` FOREIGN KEY (`recipient_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Chat messages table';

-- Table `pms_product`
CREATE TABLE `pms_product` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Product ID',
    `user_id` BIGINT NOT NULL COMMENT 'Seller user ID',
    `name` VARCHAR(255) NOT NULL COMMENT 'Product name',
    `description` TEXT NULL COMMENT 'Product description',
    `price` DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT 'Price',
    `stock` INT NOT NULL DEFAULT 1 COMMENT 'Stock (default 1 for used items)',
    `condition_level` INT NOT NULL DEFAULT 9 COMMENT 'Condition (1-10)',
    `location` VARCHAR(255) NULL COMMENT 'Location',
    `category` VARCHAR(100) NULL DEFAULT 'other' COMMENT 'Product category',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT 'Status: 1=For Sale, 2=Sold, 0=Delisted',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Last update time',
    PRIMARY KEY (`id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_status` (`status`),
    INDEX `idx_price` (`price`),
    INDEX `idx_category` (`category`),
    CONSTRAINT `fk_product_user` FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Products table';

-- Table `pms_product_media`
CREATE TABLE `pms_product_media` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Media ID',
    `product_id` BIGINT NOT NULL COMMENT 'Product ID',
    `url` VARCHAR(1024) NOT NULL COMMENT 'Media URL',
    `media_type` TINYINT NOT NULL COMMENT 'Media type: 1=Image, 2=Video',
    `sort_order` INT NULL DEFAULT 0 COMMENT 'Sort order',
    PRIMARY KEY (`id`),
    INDEX `idx_product_id` (`product_id`),
    CONSTRAINT `fk_media_product` FOREIGN KEY (`product_id`) REFERENCES `pms_product`(`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Product media table';

-- Table `pms_report`
CREATE TABLE `pms_report` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Report ID',
    `product_id` BIGINT NOT NULL COMMENT 'Reported Product ID',
    `reporter_id` BIGINT NOT NULL COMMENT 'Reporter User ID',
    `reason` VARCHAR(255) NOT NULL COMMENT 'Reason for reporting',
    `description` TEXT NULL COMMENT 'Detailed description of the report',
    `status` ENUM('PENDING', 'APPROVED', 'REJECTED') NOT NULL DEFAULT 'PENDING' COMMENT 'Report status',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Last update time',
    PRIMARY KEY (`id`),
    INDEX `idx_product_id` (`product_id`),
    INDEX `idx_reporter_id` (`reporter_id`),
    CONSTRAINT `fk_report_product` FOREIGN KEY (`product_id`) REFERENCES `pms_product`(`id`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `fk_report_reporter` FOREIGN KEY (`reporter_id`) REFERENCES `users`(`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Product reports table';

-- Table `user_favorites`
CREATE TABLE `user_favorites` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Favorite ID',
    `user_id` BIGINT NOT NULL COMMENT 'User ID',
    `product_id` BIGINT NOT NULL COMMENT 'Product ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_product` (`user_id`, `product_id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_product_id` (`product_id`),
    CONSTRAINT `fk_favorite_user` FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `fk_favorite_product` FOREIGN KEY (`product_id`) REFERENCES `pms_product`(`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='User favorites table';

-- Table `shopping_cart`
CREATE TABLE `shopping_cart` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Cart ID',
    `user_id` BIGINT NOT NULL COMMENT 'User ID',
    `product_id` BIGINT NOT NULL COMMENT 'Product ID',
    `quantity` INT NOT NULL DEFAULT 1 COMMENT 'Quantity',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_product` (`user_id`, `product_id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_product_id` (`product_id`),
    CONSTRAINT `fk_cart_user` FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `fk_cart_product` FOREIGN KEY (`product_id`) REFERENCES `pms_product`(`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Shopping cart table';

-- Orders
CREATE TABLE `orders` (
                          `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Order ID',
                          `user_id` BIGINT NOT NULL COMMENT 'User ID',
                          `total_amount` DECIMAL(10, 2) NOT NULL COMMENT 'Total amount',
                          `status` VARCHAR(20) NOT NULL COMMENT 'Order status (PENDING, PAID, SHIPPED, DELIVERED, CANCELED, EXPIRED)',
                          `payment_method` VARCHAR(50) NULL COMMENT 'Payment method (alipay, wechat, bank, balance)',
                          `expire_time` TIMESTAMP NULL COMMENT 'Order expiry time (15 minutes after creation)',
                          `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created at',
                          `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated at',
                          PRIMARY KEY (`id`),
                          FOREIGN KEY (`user_id`) REFERENCES `users`(`id`),
                          INDEX `idx_status_expire` (`status`, `expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Order items
CREATE TABLE `order_items` (
                               `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Order item ID',
                               `order_id` BIGINT NOT NULL COMMENT 'Order ID',
                               `product_id` BIGINT NOT NULL COMMENT 'Product ID',
                               `quantity` INT NOT NULL COMMENT 'Quantity',
                               `price` DECIMAL(10, 2) NOT NULL COMMENT 'Unit price',
                               `from_cart` BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Whether it came from the cart',
                               PRIMARY KEY (`id`),
                               FOREIGN KEY (`order_id`) REFERENCES `orders`(`id`),
                               FOREIGN KEY (`product_id`) REFERENCES `pms_product`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Transactions (simulated payments)
CREATE TABLE `transactions` (
                                `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Transaction ID',
                                `order_id` BIGINT NOT NULL COMMENT 'Order ID',
                                `amount` DECIMAL(10, 2) NOT NULL COMMENT 'Transaction amount',
                                `payment_method` VARCHAR(50) NULL COMMENT 'Payment method (alipay, wechat, bank, balance)',
                                `transaction_type` VARCHAR(20) NOT NULL COMMENT 'Transaction type (PAYMENT, REFUND)',
                                `status` VARCHAR(20) NOT NULL COMMENT 'Transaction status (SUCCESS, FAILED)',
                                `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created at',
                                PRIMARY KEY (`id`),
                                FOREIGN KEY (`order_id`) REFERENCES `orders`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Bargain activities
CREATE TABLE `bargain_activity` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Bargain activity ID',
    `user_id` BIGINT NOT NULL COMMENT 'ID of the user who started it',
    `product_id` BIGINT NOT NULL COMMENT 'Product ID',
    `original_price` DECIMAL(10, 2) NOT NULL COMMENT 'Original price',
    `target_price` DECIMAL(10, 2) NOT NULL COMMENT 'Target price (lowest)',
    `current_price` DECIMAL(10, 2) NOT NULL COMMENT 'Current price',
    `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Status: ACTIVE - in progress, SUCCESS - succeeded, EXPIRED - expired',
    `expire_time` TIMESTAMP NOT NULL COMMENT 'Expiry time',
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created at',
    `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated at',
    PRIMARY KEY (`id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_product_id` (`product_id`),
    INDEX `idx_status` (`status`),
    FOREIGN KEY (`user_id`) REFERENCES `users`(`id`),
    FOREIGN KEY (`product_id`) REFERENCES `pms_product`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Bargain activities';

-- Bargain help records
CREATE TABLE `bargain_help` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Help record ID',
    `bargain_id` BIGINT NOT NULL COMMENT 'Bargain activity ID',
    `helper_id` BIGINT NULL COMMENT 'Helper user ID (nullable, guests can help)',
    `helper_name` VARCHAR(100) NULL COMMENT 'Helper nickname (used for guests)',
    `cut_amount` DECIMAL(10, 2) NOT NULL COMMENT 'Amount cut',
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Helped at',
    PRIMARY KEY (`id`),
    INDEX `idx_bargain_id` (`bargain_id`),
    INDEX `idx_helper_id` (`helper_id`),
    FOREIGN KEY (`bargain_id`) REFERENCES `bargain_activity`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Bargain help records';

-- Reviews
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
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created at',
    `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated at',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_order_id` (`order_id`),
    INDEX `idx_product_id` (`product_id`),
    INDEX `idx_buyer_id` (`buyer_id`),
    INDEX `idx_seller_id` (`seller_id`),
    CONSTRAINT `fk_review_product` FOREIGN KEY (`product_id`) REFERENCES `pms_product`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Reviews';

-- Credit scores
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
    `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated at',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_user_id` (`user_id`),
    CONSTRAINT `fk_credit_score_user` FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Credit scores';

-- =================================================================
-- Seeding Essential Data
-- =================================================================

-- Insert base roles
INSERT INTO `roles` (`id`, `name`) VALUES (1, 'ROLE_USER'), (2, 'ROLE_ADMIN')
ON DUPLICATE KEY UPDATE name=VALUES(name);

-- Create a default admin account (username: admin, password: admin123)
-- BCrypt hash for 'admin123'
INSERT INTO `users` (`id`, `username`, `display_name`, `password_hash`, `email`, `avatar_url`)
VALUES (1, 'admin', 'System Administrator', '$2a$12$9l1r7OVMYW3xsv/JQchZKutlvkIJgopmbNC3jEA3hUkNbN/ivzMn2', 'admin@rebook.trade', 'https://i.pravatar.cc/150?u=admin')
ON DUPLICATE KEY UPDATE username=VALUES(username), display_name=VALUES(display_name), password_hash=VALUES(password_hash);

-- Assign roles to admin user
INSERT INTO `user_roles` (`user_id`, `role_id`) VALUES (1, 1), (1, 2)
ON DUPLICATE KEY UPDATE user_id=VALUES(user_id);

-- =================================================================
-- Reputation System Test Data
-- Users with different seller levels (1-5)
-- Password for all users: admin123
-- =================================================================

-- 1. Create Users
-- IDs 101-105 to avoid conflicts
INSERT INTO `users` (`id`, `username`, `display_name`, `password_hash`, `email`, `avatar_url`, `balance`) VALUES
(101, 'seller_lvl1', 'Seller Level 1', '$2a$12$9l1r7OVMYW3xsv/JQchZKutlvkIJgopmbNC3jEA3hUkNbN/ivzMn2', 'seller1@test.com', 'https://i.pravatar.cc/150?u=seller1', 1000.00),
(102, 'seller_lvl2', 'Seller Level 2', '$2a$12$9l1r7OVMYW3xsv/JQchZKutlvkIJgopmbNC3jEA3hUkNbN/ivzMn2', 'seller2@test.com', 'https://i.pravatar.cc/150?u=seller2', 2000.00),
(103, 'seller_lvl3', 'Seller Level 3', '$2a$12$9l1r7OVMYW3xsv/JQchZKutlvkIJgopmbNC3jEA3hUkNbN/ivzMn2', 'seller3@test.com', 'https://i.pravatar.cc/150?u=seller3', 3000.00),
(104, 'seller_lvl4', 'Seller Level 4', '$2a$12$9l1r7OVMYW3xsv/JQchZKutlvkIJgopmbNC3jEA3hUkNbN/ivzMn2', 'seller4@test.com', 'https://i.pravatar.cc/150?u=seller4', 4000.00),
(105, 'seller_lvl5', 'Seller Level 5', '$2a$12$9l1r7OVMYW3xsv/JQchZKutlvkIJgopmbNC3jEA3hUkNbN/ivzMn2', 'seller5@test.com', 'https://i.pravatar.cc/150?u=seller5', 5000.00)
ON DUPLICATE KEY UPDATE 
    password_hash=VALUES(password_hash), 
    display_name=VALUES(display_name),
    email=VALUES(email);

-- 2. Assign Roles (ROLE_USER = 1)
INSERT INTO `user_roles` (`user_id`, `role_id`) VALUES
(101, 1),
(102, 1),
(103, 1),
(104, 1),
(105, 1)
ON DUPLICATE KEY UPDATE role_id=VALUES(role_id);

-- 3. Create Credit Scores / Reputation
INSERT INTO `credit_score` (`user_id`, `total_score`, `level`, `total_sales`, `average_seller_rating`, `positive_reviews`, `neutral_reviews`, `negative_reviews`) VALUES
(101, 100, 1, 10, 3.0, 8, 1, 1),
(102, 300, 2, 30, 3.5, 25, 3, 2),
(103, 600, 3, 60, 4.0, 55, 4, 1),
(104, 1000, 4, 100, 4.5, 95, 4, 1),
(105, 2000, 5, 200, 5.0, 200, 0, 0)
ON DUPLICATE KEY UPDATE
    total_score=VALUES(total_score),
    level=VALUES(level),
    average_seller_rating=VALUES(average_seller_rating);

-- =================================================================
-- End of Script
-- =================================================================
SELECT 'Database structure and essential data seeded successfully.' AS status;
ALTER TABLE chat_message CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;