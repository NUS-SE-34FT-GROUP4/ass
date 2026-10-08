package sg.edu.nus.iss.c2csectrade.event;

import sg.edu.nus.iss.c2csectrade.dto.ChatMessageDTO;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Receives chat fanout messages on this instance's exclusive queue and
 * pushes them to users connected to this instance. If the target user is
 * connected to another instance, the local simple broker silently drops
 * the message; that instance delivers it instead.
 */
@Component
public class ChatMessageFanoutConsumer {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @RabbitListener(queues = "#{chatFanoutQueue.name}")
    public void onChatMessage(Events.ChatFanoutEvent event) {
        try {
            ChatMessageDTO message = ChatMessageDTO.builder()
                    .sender(event.getSender())
                    .recipient(event.getRecipient())
                    .content(event.getContent())
                    .timestamp(event.getTimestamp())
                    .isSystemMessage(event.getIsSystemMessage())
                    .build();
            messagingTemplate.convertAndSendToUser(
                event.getTargetUsername(),
                "/queue/private",
                message
            );
        } catch (Exception e) {
            System.err.println("Error delivering chat fanout message to "
                + event.getTargetUsername() + ": " + e.getMessage());
        }
    }
}
