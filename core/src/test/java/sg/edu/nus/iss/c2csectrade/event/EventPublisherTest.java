package sg.edu.nus.iss.c2csectrade.event;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.amqp.AmqpException;

import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import sg.edu.nus.iss.c2csectrade.config.RabbitMQConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class EventPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private EventPublisher eventPublisher;

    @Test
    void publishProductDelistedSendsToTheProductExchange() {
        Events.ProductDelistedEvent event = new Events.ProductDelistedEvent(42L, "Desk lamp", "seller1");

        eventPublisher.publishProductDelisted(event);

        ArgumentCaptor<Events.ProductDelistedEvent> sent = ArgumentCaptor.forClass(Events.ProductDelistedEvent.class);
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.PRODUCT_EXCHANGE),
                eq(RabbitMQConfig.PRODUCT_DELISTED_ROUTING_KEY),
                sent.capture());
        verifyNoMoreInteractions(rabbitTemplate);
        assertThat(sent.getValue()).isEqualTo(event);
    }

    @Test
    void rabbitFailureDoesNotThrowBecauseTheProductIsAlreadyDelisted() {
        org.mockito.Mockito.doThrow(new RuntimeException("broker down"))
                .when(rabbitTemplate).convertAndSend(
                        eq(RabbitMQConfig.PRODUCT_EXCHANGE),
                        eq(RabbitMQConfig.PRODUCT_DELISTED_ROUTING_KEY),
                        org.mockito.ArgumentMatchers.any(Object.class));

        org.assertj.core.api.Assertions.assertThatCode(() ->
                eventPublisher.publishProductDelisted(
                        new Events.ProductDelistedEvent(42L, "Desk lamp", "seller1"))
        ).doesNotThrowAnyException();
    }

    /** Every other event, with the exchange and routing key its consumers bind to. */
    static Stream<Arguments> everyOtherEvent() {
        Events.OrderPaidEvent paid = new Events.OrderPaidEvent();
        Events.OrderCanceledEvent canceled = new Events.OrderCanceledEvent();
        Events.OrderCompletedEvent completed = new Events.OrderCompletedEvent();
        Events.ProductChangedEvent changed = new Events.ProductChangedEvent();
        Events.ProductDeletedEvent deleted = new Events.ProductDeletedEvent();
        Events.ProductStockLowEvent stockLow = new Events.ProductStockLowEvent();
        Events.RecommendationUpdateEvent recommendation = new Events.RecommendationUpdateEvent();
        Events.SystemMessageEvent systemMessage = new Events.SystemMessageEvent();
        Events.EmailNotificationEvent email = new Events.EmailNotificationEvent();
        return Stream.of(
                route("order paid", p -> p.publishOrderPaid(paid), paid,
                        RabbitMQConfig.ORDER_EXCHANGE, RabbitMQConfig.ORDER_PAID_ROUTING_KEY),
                route("order canceled", p -> p.publishOrderCanceled(canceled), canceled,
                        RabbitMQConfig.ORDER_EXCHANGE, RabbitMQConfig.ORDER_CANCELED_ROUTING_KEY),
                route("order completed", p -> p.publishOrderCompleted(completed), completed,
                        RabbitMQConfig.ORDER_EXCHANGE, RabbitMQConfig.ORDER_COMPLETED_ROUTING_KEY),
                route("product created", p -> p.publishProductCreated(changed), changed,
                        RabbitMQConfig.PRODUCT_EXCHANGE, RabbitMQConfig.PRODUCT_CREATED_ROUTING_KEY),
                route("product updated", p -> p.publishProductUpdated(changed), changed,
                        RabbitMQConfig.PRODUCT_EXCHANGE, RabbitMQConfig.PRODUCT_UPDATED_ROUTING_KEY),
                route("product deleted", p -> p.publishProductDeleted(deleted), deleted,
                        RabbitMQConfig.PRODUCT_EXCHANGE, RabbitMQConfig.PRODUCT_DELETED_ROUTING_KEY),
                route("stock low", p -> p.publishProductStockLow(stockLow), stockLow,
                        RabbitMQConfig.PRODUCT_EXCHANGE, RabbitMQConfig.PRODUCT_STOCK_LOW_ROUTING_KEY),
                route("recommendation update", p -> p.publishRecommendationUpdate(recommendation), recommendation,
                        RabbitMQConfig.RECOMMENDATION_EXCHANGE, RabbitMQConfig.RECOMMENDATION_UPDATE_ROUTING_KEY),
                route("system message", p -> p.publishSystemMessage(systemMessage), systemMessage,
                        RabbitMQConfig.NOTIFICATION_EXCHANGE, RabbitMQConfig.SYSTEM_MESSAGE_ROUTING_KEY),
                route("email notification", p -> p.publishEmailNotification(email), email,
                        RabbitMQConfig.NOTIFICATION_EXCHANGE, RabbitMQConfig.EMAIL_NOTIFICATION_ROUTING_KEY));
    }

    private static Arguments route(String name, Consumer<EventPublisher> publish, Object event,
            String exchange, String routingKey) {
        return Arguments.of(name, publish, event, exchange, routingKey);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyOtherEvent")
    void eachEventGoesToItsExchangeWithItsRoutingKey(String name, Consumer<EventPublisher> publish, Object event,
            String exchange, String routingKey) {
        publish.accept(eventPublisher);

        verify(rabbitTemplate).convertAndSend(exchange, routingKey, event);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyOtherEvent")
    void aBrokerFailureNeverReachesTheCaller(String name, Consumer<EventPublisher> publish, Object event,
            String exchange, String routingKey) {
        doThrow(new AmqpException("broker down")).when(rabbitTemplate).convertAndSend(exchange, routingKey, event);

        assertThatCode(() -> publish.accept(eventPublisher)).doesNotThrowAnyException();
    }
}
