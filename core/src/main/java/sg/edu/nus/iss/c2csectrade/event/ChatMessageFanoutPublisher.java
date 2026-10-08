package sg.edu.nus.iss.c2csectrade.event;

import sg.edu.nus.iss.c2csectrade.config.RabbitMQConfig;
import sg.edu.nus.iss.c2csectrade.dto.ChatMessageDTO;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Publishes chat and system messages to the chat fanout exchange.
 * Every Chat instance has its own queue bound to the exchange, so the
 * message reaches every instance; each instance then pushes it only to
 * users connected to itself.
 */
@Service
public class ChatMessageFanoutPublisher {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * Deliver a message to a user, regardless of which instance they are connected to.
     * The message is expected to be already persisted in the database.
     */
    public void deliverToUser(String targetUsername, ChatMessageDTO message) {
        try {
            Events.ChatFanoutEvent event = new Events.ChatFanoutEvent(
                targetUsername,
                message.getSender(),
                message.getRecipient(),
                message.getContent(),
                message.getTimestamp(),
                message.getIsSystemMessage()
            );
            // Fanout exchange ignores the routing key
            rabbitTemplate.convertAndSend(RabbitMQConfig.CHAT_FANOUT_EXCHANGE, "", event);
        } catch (Exception e) {
            System.err.println("Failed to publish chat fanout message for user "
                + targetUsername + ": " + e.getMessage());
            // Don't throw - the message is already in the database, the user can pull it via chat history
        }
    }
}
