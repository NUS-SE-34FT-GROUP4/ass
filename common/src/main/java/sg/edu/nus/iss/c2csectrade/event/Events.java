package sg.edu.nus.iss.c2csectrade.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;

/**
 * Event DTOs for Message Queue
 */
public class Events {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderPaidEvent implements Serializable {
        private Integer orderId;
        private Integer userId;
        private BigDecimal amount;
        private String paymentMethod;
        private Date paidAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderCanceledEvent implements Serializable {
        private Integer orderId;
        private Integer userId;
        private String reason;
        private Date canceledAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderCompletedEvent implements Serializable {
        private Integer orderId;
        private Integer userId;
        private Integer sellerId;
        private Date completedAt;
    }

    /**
     * Sent by Core on routing keys product.created and product.updated.
     * Carries every field the search index stores, so Search can upsert
     * the document without calling back into Core.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductChangedEvent implements Serializable {
        private Long productId;
        private Long userId;
        private String name;
        private String description;
        private BigDecimal price;
        private Integer conditionLevel;
        private String location;
        private String category;
        private Integer status;
        private LocalDateTime createdAt;
    }

    /**
     * Sent by Core on routing key product.deleted.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductDeletedEvent implements Serializable {
        private Long productId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductStockLowEvent implements Serializable {
        private Long productId;
        private String productName;
        private Integer currentStock;
        private Long sellerId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecommendationUpdateEvent implements Serializable {
        private Long productId;
        private String action; // "view", "favorite", "cart", "order", "review"
        private Long userId;
        private Date timestamp;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SystemMessageEvent implements Serializable {
        private Integer userId;
        private String messageType; // "ORDER", "PAYMENT", "REVIEW", "SYSTEM"
        private String title;
        private String content;
        private Date timestamp;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmailNotificationEvent implements Serializable {
        private String toEmail;
        private String subject;
        private String content;
        private String template;
        private Date timestamp;
    }

    /**
     * Chat cross-instance delivery. Published to the chat fanout exchange;
     * every Chat instance binds its own exclusive queue to the exchange and
     * pushes the message only to users connected to itself. If the target
     * user is offline, no instance delivers it; the message has already been
     * persisted and can be pulled via the chat history REST API.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatFanoutEvent implements Serializable {
        private String targetUsername; // user the message should be delivered to
        private String sender;
        private String recipient;
        private String content;
        private Date timestamp;
        private Boolean isSystemMessage;
    }
}

