package sg.edu.nus.iss.c2csectrade.search.event;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConverter;
import sg.edu.nus.iss.c2csectrade.event.Events;
import sg.edu.nus.iss.c2csectrade.search.config.RabbitMQConfig;
import sg.edu.nus.iss.c2csectrade.search.service.ProductIndexService;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class ProductIndexConsumerTest {

    @Mock
    private ProductIndexService indexService;

    @InjectMocks
    private ProductIndexConsumer consumer;

    @Test
    void changedProductIsIndexed() throws Exception {
        Events.ProductChangedEvent event = changedEvent();

        consumer.handleProductChanged(event);

        verify(indexService).index(event);
        verifyNoMoreInteractions(indexService);
    }

    @Test
    void deletedProductIsRemovedFromTheIndex() throws Exception {
        consumer.handleProductDeleted(new Events.ProductDeletedEvent(42L));

        verify(indexService).delete(42L);
        verifyNoMoreInteractions(indexService);
    }

    /** The listener retries only if the failure reaches it. */
    @Test
    void indexFailureIsNotSwallowed() throws Exception {
        Events.ProductChangedEvent event = changedEvent();
        doThrow(new IOException("connection refused")).when(indexService).index(event);

        assertThatThrownBy(() -> consumer.handleProductChanged(event)).isInstanceOf(IOException.class);
    }

    /**
     * Core publishes with a converter configured like this one. Check the type
     * header names the shared event class, and every field reads back, dates
     * and decimals included.
     */
    @Test
    void messagesRoundTripThroughTheApplicationConverter() {
        MessageConverter converter = new RabbitMQConfig().jsonMessageConverter();

        Message changed = converter.toMessage(changedEvent(), new MessageProperties());
        Message deleted = converter.toMessage(new Events.ProductDeletedEvent(42L), new MessageProperties());

        assertThat(changed.getMessageProperties().getHeaders().get("__TypeId__"))
                .isEqualTo("sg.edu.nus.iss.c2csectrade.event.Events$ProductChangedEvent");
        assertThat(converter.fromMessage(changed)).isEqualTo(changedEvent());
        assertThat(converter.fromMessage(deleted)).isEqualTo(new Events.ProductDeletedEvent(42L));
    }

    private static Events.ProductChangedEvent changedEvent() {
        return new Events.ProductChangedEvent(
                42L, 7L, "Desk lamp", "Barely used", new BigDecimal("12.50"),
                9, "Kent Ridge", "Home", 1, LocalDateTime.of(2026, 10, 8, 9, 30));
    }
}
