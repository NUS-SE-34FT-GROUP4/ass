package sg.edu.nus.iss.c2csectrade.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ Configuration for Distributed Message Queue
 * Handles async event processing: order events, notification events, recommendation updates
 */
@Configuration
public class RabbitMQConfig {

    // ============ Order Events ============
    public static final String ORDER_EXCHANGE = "order.exchange";
    public static final String ORDER_PAID_QUEUE = "order.paid.queue";
    public static final String ORDER_PAID_ROUTING_KEY = "order.paid";
    public static final String ORDER_CANCELED_QUEUE = "order.canceled.queue";
    public static final String ORDER_CANCELED_ROUTING_KEY = "order.canceled";
    public static final String ORDER_COMPLETED_QUEUE = "order.completed.queue";
    public static final String ORDER_COMPLETED_ROUTING_KEY = "order.completed";

    // ============ Notification Events ============
    public static final String NOTIFICATION_EXCHANGE = "notification.exchange";
    public static final String EMAIL_NOTIFICATION_QUEUE = "notification.email.queue";
    public static final String EMAIL_NOTIFICATION_ROUTING_KEY = "notification.email";
    public static final String SYSTEM_MESSAGE_QUEUE = "notification.system.queue";
    public static final String SYSTEM_MESSAGE_ROUTING_KEY = "notification.system";

    // ============ Recommendation Events ============
    public static final String RECOMMENDATION_EXCHANGE = "recommendation.exchange";
    public static final String RECOMMENDATION_UPDATE_QUEUE = "recommendation.update.queue";
    public static final String RECOMMENDATION_UPDATE_ROUTING_KEY = "recommendation.update";

    // ============ Product Events ============
    public static final String PRODUCT_EXCHANGE = "product.exchange";
    public static final String PRODUCT_CREATED_ROUTING_KEY = "product.created";
    public static final String PRODUCT_UPDATED_ROUTING_KEY = "product.updated";
    public static final String PRODUCT_DELETED_ROUTING_KEY = "product.deleted";
    // One queue for all three keys so Search applies changes in the order they were made
    public static final String SEARCH_PRODUCT_QUEUE = "search.product.queue";
    public static final String PRODUCT_STOCK_LOW_QUEUE = "product.stock.low.queue";
    public static final String PRODUCT_STOCK_LOW_ROUTING_KEY = "product.stock.low";

    /**
     * JSON Message Converter
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        // Consumers that share a queue pick the handler from the type header,
        // so the converter must accept our event classes; trust only that package.
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTrustedPackages("sg.edu.nus.iss.c2csectrade.event");
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }

    /**
     * RabbitTemplate with JSON converter
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }

    // ============ Order Exchange and Queues ============

    @Bean
    public TopicExchange orderExchange() {
        return new TopicExchange(ORDER_EXCHANGE, true, false);
    }

    @Bean
    public Queue orderPaidQueue() {
        return QueueBuilder.durable(ORDER_PAID_QUEUE)
                .withArgument("x-message-ttl", 3600000) // 1 hour TTL
                .build();
    }

    @Bean
    public Binding orderPaidBinding() {
        return BindingBuilder.bind(orderPaidQueue())
                .to(orderExchange())
                .with(ORDER_PAID_ROUTING_KEY);
    }

    @Bean
    public Queue orderCanceledQueue() {
        return QueueBuilder.durable(ORDER_CANCELED_QUEUE).build();
    }

    @Bean
    public Binding orderCanceledBinding() {
        return BindingBuilder.bind(orderCanceledQueue())
                .to(orderExchange())
                .with(ORDER_CANCELED_ROUTING_KEY);
    }

    @Bean
    public Queue orderCompletedQueue() {
        return QueueBuilder.durable(ORDER_COMPLETED_QUEUE).build();
    }

    @Bean
    public Binding orderCompletedBinding() {
        return BindingBuilder.bind(orderCompletedQueue())
                .to(orderExchange())
                .with(ORDER_COMPLETED_ROUTING_KEY);
    }

    // ============ Notification Exchange and Queues ============

    @Bean
    public TopicExchange notificationExchange() {
        return new TopicExchange(NOTIFICATION_EXCHANGE, true, false);
    }

    @Bean
    public Queue emailNotificationQueue() {
        return QueueBuilder.durable(EMAIL_NOTIFICATION_QUEUE)
                .withArgument("x-message-ttl", 600000) // 10 minutes TTL
                .build();
    }

    @Bean
    public Binding emailNotificationBinding() {
        return BindingBuilder.bind(emailNotificationQueue())
                .to(notificationExchange())
                .with(EMAIL_NOTIFICATION_ROUTING_KEY);
    }

    @Bean
    public Queue systemMessageQueue() {
        return QueueBuilder.durable(SYSTEM_MESSAGE_QUEUE).build();
    }

    @Bean
    public Binding systemMessageBinding() {
        return BindingBuilder.bind(systemMessageQueue())
                .to(notificationExchange())
                .with(SYSTEM_MESSAGE_ROUTING_KEY);
    }

    // ============ Recommendation Exchange and Queues ============

    @Bean
    public TopicExchange recommendationExchange() {
        return new TopicExchange(RECOMMENDATION_EXCHANGE, true, false);
    }

    @Bean
    public Queue recommendationUpdateQueue() {
        return QueueBuilder.durable(RECOMMENDATION_UPDATE_QUEUE).build();
    }

    @Bean
    public Binding recommendationUpdateBinding() {
        return BindingBuilder.bind(recommendationUpdateQueue())
                .to(recommendationExchange())
                .with(RECOMMENDATION_UPDATE_ROUTING_KEY);
    }

    // ============ Product Exchange and Queues ============

    @Bean
    public TopicExchange productExchange() {
        return new TopicExchange(PRODUCT_EXCHANGE, true, false);
    }

    @Bean
    public Queue searchProductQueue() {
        return QueueBuilder.durable(SEARCH_PRODUCT_QUEUE).build();
    }

    @Bean
    public Binding searchProductCreatedBinding() {
        return BindingBuilder.bind(searchProductQueue())
                .to(productExchange())
                .with(PRODUCT_CREATED_ROUTING_KEY);
    }

    @Bean
    public Binding searchProductUpdatedBinding() {
        return BindingBuilder.bind(searchProductQueue())
                .to(productExchange())
                .with(PRODUCT_UPDATED_ROUTING_KEY);
    }

    @Bean
    public Binding searchProductDeletedBinding() {
        return BindingBuilder.bind(searchProductQueue())
                .to(productExchange())
                .with(PRODUCT_DELETED_ROUTING_KEY);
    }

    @Bean
    public Queue productStockLowQueue() {
        return QueueBuilder.durable(PRODUCT_STOCK_LOW_QUEUE).build();
    }

    @Bean
    public Binding productStockLowBinding() {
        return BindingBuilder.bind(productStockLowQueue())
                .to(productExchange())
                .with(PRODUCT_STOCK_LOW_ROUTING_KEY);
    }
}

