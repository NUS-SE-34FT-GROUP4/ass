package sg.edu.nus.iss.c2csectrade.event;

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
}
