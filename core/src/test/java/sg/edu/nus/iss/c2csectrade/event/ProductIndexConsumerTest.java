package sg.edu.nus.iss.c2csectrade.event;

import sg.edu.nus.iss.c2csectrade.config.RabbitMQConfig;
import sg.edu.nus.iss.c2csectrade.service.SearchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConverter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class ProductIndexConsumerTest {

    @Mock
    private SearchService searchService;

    @InjectMocks
    private ProductIndexConsumer consumer;

    @Test
    void changedProductIsIndexed() {
        Events.ProductChangedEvent event = changedEvent();

        consumer.handleProductChanged(event);

        verify(searchService).indexProduct(event);
        verifyNoMoreInteractions(searchService);
    }

    @Test
    void deletedProductIsRemovedFromTheIndex() {
        consumer.handleProductDeleted(new Events.ProductDeletedEvent(42L));

        verify(searchService).deleteProduct(42L);
        verifyNoMoreInteractions(searchService);
    }

    /**
     * Both message types share one queue, so the consumer picks the handler from
     * the type header. Check the converter the application uses writes that header
     * and reads every field back, dates and decimals included.
     */
    @Test
    void messagesRoundTripThroughTheApplicationConverter() {
        MessageConverter converter = new RabbitMQConfig().jsonMessageConverter();

        Message changed = converter.toMessage(changedEvent(), new MessageProperties());
        Message deleted = converter.toMessage(new Events.ProductDeletedEvent(42L), new MessageProperties());

        assertThat(converter.fromMessage(changed)).isEqualTo(changedEvent());
        assertThat(converter.fromMessage(deleted)).isEqualTo(new Events.ProductDeletedEvent(42L));
    }

    private static Events.ProductChangedEvent changedEvent() {
        return new Events.ProductChangedEvent(
                42L, 7L, "Desk lamp", "Barely used", new BigDecimal("12.50"),
                9, "Kent Ridge", "Home", 1, LocalDateTime.of(2026, 10, 8, 9, 30));
    }
}
