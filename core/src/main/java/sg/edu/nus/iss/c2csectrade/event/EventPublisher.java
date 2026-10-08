package sg.edu.nus.iss.c2csectrade.event;

import sg.edu.nus.iss.c2csectrade.config.RabbitMQConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Event Publisher Service
 * Publishes events to RabbitMQ for async processing
 */
@Service
public class EventPublisher {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * Publish order paid event
     */
    public void publishOrderPaid(Events.OrderPaidEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                RabbitMQConfig.ORDER_EXCHANGE,
                RabbitMQConfig.ORDER_PAID_ROUTING_KEY,
                event
            );
            System.out.println("Published OrderPaidEvent: " + event.getOrderId());
        } catch (Exception e) {
            System.err.println("Failed to publish OrderPaidEvent: " + e.getMessage());
            // Don't throw - event publishing should not break main flow
        }
    }

    /**
     * Publish order canceled event
     */
    public void publishOrderCanceled(Events.OrderCanceledEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                RabbitMQConfig.ORDER_EXCHANGE,
                RabbitMQConfig.ORDER_CANCELED_ROUTING_KEY,
                event
            );
            System.out.println("Published OrderCanceledEvent: " + event.getOrderId());
        } catch (Exception e) {
            System.err.println("Failed to publish OrderCanceledEvent: " + e.getMessage());
        }
    }

    /**
     * Publish order completed event
     */
    public void publishOrderCompleted(Events.OrderCompletedEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                RabbitMQConfig.ORDER_EXCHANGE,
                RabbitMQConfig.ORDER_COMPLETED_ROUTING_KEY,
                event
            );
            System.out.println("Published OrderCompletedEvent: " + event.getOrderId());
        } catch (Exception e) {
            System.err.println("Failed to publish OrderCompletedEvent: " + e.getMessage());
        }
    }

    /**
     * Publish product created event
     */
    public void publishProductCreated(Events.ProductChangedEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                RabbitMQConfig.PRODUCT_EXCHANGE,
                RabbitMQConfig.PRODUCT_CREATED_ROUTING_KEY,
                event
            );
            System.out.println("Published product.created: " + event.getProductId());
        } catch (Exception e) {
            System.err.println("Failed to publish product.created: " + e.getMessage());
        }
    }

    /**
     * Publish product updated event
     */
    public void publishProductUpdated(Events.ProductChangedEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                RabbitMQConfig.PRODUCT_EXCHANGE,
                RabbitMQConfig.PRODUCT_UPDATED_ROUTING_KEY,
                event
            );
            System.out.println("Published product.updated: " + event.getProductId());
        } catch (Exception e) {
            System.err.println("Failed to publish product.updated: " + e.getMessage());
        }
    }

    /**
     * Publish product deleted event
     */
    public void publishProductDeleted(Events.ProductDeletedEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                RabbitMQConfig.PRODUCT_EXCHANGE,
                RabbitMQConfig.PRODUCT_DELETED_ROUTING_KEY,
                event
            );
            System.out.println("Published product.deleted: " + event.getProductId());
        } catch (Exception e) {
            System.err.println("Failed to publish product.deleted: " + e.getMessage());
        }
    }

    /**
     * Publish product stock low event
     */
    public void publishProductStockLow(Events.ProductStockLowEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                RabbitMQConfig.PRODUCT_EXCHANGE,
                RabbitMQConfig.PRODUCT_STOCK_LOW_ROUTING_KEY,
                event
            );
            System.out.println("Published ProductStockLowEvent: " + event.getProductId());
        } catch (Exception e) {
            System.err.println("Failed to publish ProductStockLowEvent: " + e.getMessage());
        }
    }

    /**
     * Publish recommendation update event
     */
    public void publishRecommendationUpdate(Events.RecommendationUpdateEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                RabbitMQConfig.RECOMMENDATION_EXCHANGE,
                RabbitMQConfig.RECOMMENDATION_UPDATE_ROUTING_KEY,
                event
            );
        } catch (Exception e) {
            System.err.println("Failed to publish RecommendationUpdateEvent: " + e.getMessage());
        }
    }

    /**
     * Publish system message event
     */
    public void publishSystemMessage(Events.SystemMessageEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                RabbitMQConfig.NOTIFICATION_EXCHANGE,
                RabbitMQConfig.SYSTEM_MESSAGE_ROUTING_KEY,
                event
            );
            System.out.println("Published SystemMessageEvent to user: " + event.getUserId());
        } catch (Exception e) {
            System.err.println("Failed to publish SystemMessageEvent: " + e.getMessage());
        }
    }

    /**
     * Publish email notification event
     */
    public void publishEmailNotification(Events.EmailNotificationEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                RabbitMQConfig.NOTIFICATION_EXCHANGE,
                RabbitMQConfig.EMAIL_NOTIFICATION_ROUTING_KEY,
                event
            );
            System.out.println("Published EmailNotificationEvent to: " + event.getToEmail());
        } catch (Exception e) {
            System.err.println("Failed to publish EmailNotificationEvent: " + e.getMessage());
        }
    }
}

