package sg.edu.nus.iss.c2csectrade.chat.event;

import sg.edu.nus.iss.c2csectrade.chat.config.RabbitMQConfig;
import sg.edu.nus.iss.c2csectrade.chat.dto.ChatMessageDTO;
import sg.edu.nus.iss.c2csectrade.chat.entity.ChatMessage;
import sg.edu.nus.iss.c2csectrade.chat.service.ChatMessageService;
import sg.edu.nus.iss.c2csectrade.event.Events;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Turns a product delisting into a system message for the seller: persists
 * it to chat_message, then fans it out so whichever instance the seller is
 * connected to pushes it over WebSocket.
 */
@Component
public class ProductDelistedConsumer {

    @Autowired
    private ChatMessageService chatMessageService;

    @Autowired
    private ChatMessageFanoutPublisher chatMessageFanoutPublisher;

    @RabbitListener(queues = RabbitMQConfig.PRODUCT_DELISTED_QUEUE)
    public void onProductDelisted(Events.ProductDelistedEvent event) {
        String content = "Your product '" + event.getProductName()
                + "' has been delisted by an administrator for violating platform rules."
                + " Please contact support if you have questions.";

        ChatMessage saved = chatMessageService.saveMessage(
                "System", event.getSellerUsername(), content, true);

        chatMessageFanoutPublisher.deliverToUser(
                event.getSellerUsername(),
                ChatMessageDTO.builder()
                        .sender("System")
                        .recipient(event.getSellerUsername())
                        .content(saved.getContent())
                        .timestamp(saved.getTimestamp())
                        .isSystemMessage(true)
                        .build());
    }
}
