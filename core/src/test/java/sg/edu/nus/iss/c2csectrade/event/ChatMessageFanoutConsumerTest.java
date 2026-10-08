package sg.edu.nus.iss.c2csectrade.event;

import sg.edu.nus.iss.c2csectrade.dto.ChatMessageDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class ChatMessageFanoutConsumerTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private ChatMessageFanoutConsumer consumer;

    @Test
    void fanoutMessageIsPushedToTheTargetUserOnThisInstance() {
        Date sent = new Date(1791465000000L);
        Events.ChatFanoutEvent event = new Events.ChatFanoutEvent(
                "seller_lvl1", "seller_lvl2", "seller_lvl1",
                "hello across instances", sent, false);

        consumer.onChatMessage(event);

        ArgumentCaptor<ChatMessageDTO> pushed = ArgumentCaptor.forClass(ChatMessageDTO.class);
        verify(messagingTemplate).convertAndSendToUser(eq("seller_lvl1"), eq("/queue/private"), pushed.capture());
        verifyNoMoreInteractions(messagingTemplate);
        assertThat(pushed.getValue()).isEqualTo(new ChatMessageDTO(
                "seller_lvl2", "seller_lvl1", "hello across instances", sent, false));
    }

    @Test
    void systemMessageKeepsItsFlagWhenPushed() {
        Events.ChatFanoutEvent event = new Events.ChatFanoutEvent(
                "seller_lvl1", "System", "seller_lvl1",
                "your product was delisted", new Date(1791465000000L), true);

        consumer.onChatMessage(event);

        ArgumentCaptor<ChatMessageDTO> pushed = ArgumentCaptor.forClass(ChatMessageDTO.class);
        verify(messagingTemplate).convertAndSendToUser(eq("seller_lvl1"), eq("/queue/private"), pushed.capture());
        assertThat(pushed.getValue().getIsSystemMessage()).isTrue();
        assertThat(pushed.getValue().getSender()).isEqualTo("System");
    }

    /**
     * Every instance consumes the same event type from its own queue, so the
     * converter the application uses must write the type header and read every
     * field back, dates included.
     */
    @Test
    void fanoutEventRoundTripsThroughTheApplicationConverter() {
        MessageConverter converter = new sg.edu.nus.iss.c2csectrade.config.RabbitMQConfig().jsonMessageConverter();
        Events.ChatFanoutEvent event = new Events.ChatFanoutEvent(
                "seller_lvl1", "seller_lvl2", "seller_lvl1",
                "round trip", new Date(1791465000000L), false);

        Object restored = converter.fromMessage(converter.toMessage(event, new org.springframework.amqp.core.MessageProperties()));

        assertThat(restored).isEqualTo(event);
    }
}
