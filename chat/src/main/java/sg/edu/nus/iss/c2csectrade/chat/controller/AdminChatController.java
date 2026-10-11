package sg.edu.nus.iss.c2csectrade.chat.controller;

import sg.edu.nus.iss.c2csectrade.chat.dto.ChatMessageDTO;
import sg.edu.nus.iss.c2csectrade.chat.entity.ChatMessage;
import sg.edu.nus.iss.c2csectrade.chat.entity.User;
import sg.edu.nus.iss.c2csectrade.chat.event.ChatMessageFanoutPublisher;
import sg.edu.nus.iss.c2csectrade.chat.service.ChatMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Admin Chat Controller
 * Handles admin operations that need to send chat notifications (delist, etc.)
 */
@RestController
@RequestMapping("/api/admin/chat")
public class AdminChatController {

    @Autowired
    private ChatMessageService chatMessageService;

    @Autowired
    private ChatMessageFanoutPublisher chatMessageFanoutPublisher;

    /**
     * Send a system notification to a user (used by admin operations like delisting)
     */
    @PostMapping("/notify")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> sendNotification(
            @RequestParam String recipient,
            @RequestParam String message) {
        try {
            // Save the system message to the database
            ChatMessage savedMessage = chatMessageService.saveMessage(
                "System",
                recipient,
                message,
                true
            );

            // Build the WebSocket message
            ChatMessageDTO responseDTO = ChatMessageDTO.builder()
                    .sender("System")
                    .recipient(recipient)
                    .content(savedMessage.getContent())
                    .timestamp(savedMessage.getTimestamp())
                    .isSystemMessage(true)
                    .build();

            // Send via fanout exchange
            chatMessageFanoutPublisher.deliverToUser(recipient, responseDTO);

            return ResponseEntity.ok().body("Notification sent");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Failed to send notification: " + e.getMessage());
        }
    }
}
