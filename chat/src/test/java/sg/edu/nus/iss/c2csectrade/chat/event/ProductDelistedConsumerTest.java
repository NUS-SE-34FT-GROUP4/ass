package sg.edu.nus.iss.c2csectrade.chat.event;

import sg.edu.nus.iss.c2csectrade.chat.dto.ChatMessageDTO;
import sg.edu.nus.iss.c2csectrade.chat.entity.ChatMessage;
import sg.edu.nus.iss.c2csectrade.chat.service.ChatMessageService;
import sg.edu.nus.iss.c2csectrade.event.Events;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.support.converter.MessageConverter;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductDelistedConsumerTest {

    @Mock
    private ChatMessageService chatMessageService;

    @Mock
    private ChatMessageFanoutPublisher chatMessageFanoutPublisher;

    @InjectMocks
    private ProductDelistedConsumer consumer;

    @Test
    void delistEventIsPersistedAsSystemMessageAndFannedOutToTheSeller() {
        Events.ProductDelistedEvent event = new Events.ProductDelistedEvent(
                42L, "Vintage Camera", "seller_lvl1");
        ChatMessage saved = new ChatMessage();
        saved.setContent("persisted content");
        saved.setTimestamp(new Date(1791465000000L));
        when(chatMessageService.saveMessage(eq("System"), eq("seller_lvl1"),
                org.mockito.ArgumentMatchers.contains("Vintage Camera"), eq(true)))
                .thenReturn(saved);

        consumer.onProductDelisted(event);

        ArgumentCaptor<ChatMessageDTO> pushed = ArgumentCaptor.forClass(ChatMessageDTO.class);
        verify(chatMessageFanoutPublisher).deliverToUser(eq("seller_lvl1"), pushed.capture());
        assertThat(pushed.getValue().getSender()).isEqualTo("System");
        assertThat(pushed.getValue().getIsSystemMessage()).isTrue();
        assertThat(pushed.getValue().getTimestamp()).isEqualTo(saved.getTimestamp());
    }

    /**
     * Core publishes this event; the converter on both sides only trusts
     * sg.edu.nus.iss.c2csectrade.event, so the class must round-trip through
     * the application converter.
     */
    @Test
    void delistEventRoundTripsThroughTheApplicationConverter() {
        MessageConverter converter = new sg.edu.nus.iss.c2csectrade.chat.config.RabbitMQConfig().jsonMessageConverter();
        Events.ProductDelistedEvent event = new Events.ProductDelistedEvent(42L, "Vintage Camera", "seller_lvl1");

        Object restored = converter.fromMessage(converter.toMessage(event, new org.springframework.amqp.core.MessageProperties()));

        assertThat(restored).isEqualTo(event);
    }
}
