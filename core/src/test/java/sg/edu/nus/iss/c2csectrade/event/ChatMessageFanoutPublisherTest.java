package sg.edu.nus.iss.c2csectrade.event;

import sg.edu.nus.iss.c2csectrade.config.RabbitMQConfig;
import sg.edu.nus.iss.c2csectrade.dto.ChatMessageDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class ChatMessageFanoutPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private ChatMessageFanoutPublisher publisher;

    @Test
    void deliverToUserPublishesToTheFanoutExchange() {
        Date sent = new Date(1791465000000L);
        ChatMessageDTO message = ChatMessageDTO.builder()
                .sender("seller_lvl2").recipient("seller_lvl1")
                .content("hello").timestamp(sent).isSystemMessage(false)
                .build();

        publisher.deliverToUser("seller_lvl1", message);

        ArgumentCaptor<Events.ChatFanoutEvent> sent0 = ArgumentCaptor.forClass(Events.ChatFanoutEvent.class);
        verify(rabbitTemplate).convertAndSend(eq(RabbitMQConfig.CHAT_FANOUT_EXCHANGE), eq(""), sent0.capture());
        verifyNoMoreInteractions(rabbitTemplate);
        assertThat(sent0.getValue()).isEqualTo(new Events.ChatFanoutEvent(
                "seller_lvl1", "seller_lvl2", "seller_lvl1", "hello", sent, false));
    }

    @Test
    void rabbitFailureDoesNotThrowBecauseTheMessageIsAlreadyPersisted() {
        org.mockito.Mockito.doThrow(new RuntimeException("broker down"))
                .when(rabbitTemplate).convertAndSend(any(String.class), any(String.class), any(Object.class));

        org.assertj.core.api.Assertions.assertThatCode(() ->
                publisher.deliverToUser("seller_lvl1", ChatMessageDTO.builder()
                        .sender("s").recipient("r").content("c")
                        .timestamp(new Date()).isSystemMessage(false).build())
        ).doesNotThrowAnyException();
    }
}
