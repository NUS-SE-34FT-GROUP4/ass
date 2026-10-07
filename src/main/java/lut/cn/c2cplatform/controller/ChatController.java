package lut.cn.c2cplatform.controller;

import lut.cn.c2cplatform.dto.ChatMessageDTO;
import lut.cn.c2cplatform.dto.ConversationDTO;
import lut.cn.c2cplatform.entity.ChatMessage;
import lut.cn.c2cplatform.service.ChatMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.stream.Collectors;

@Controller
public class ChatController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private ChatMessageService chatMessageService;

    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Payload ChatMessageDTO chatMessageDTO, Principal principal) {
        try {
            // Use the authenticated username as the sender
            String senderUsername = principal.getName();

            // Save the message to the database (a normal message, not a system message)
            ChatMessage savedMessage = chatMessageService.saveMessage(
                senderUsername,
                chatMessageDTO.getRecipient(),
                chatMessageDTO.getContent(),
                false
            );

            // Build the response DTO
            ChatMessageDTO responseDTO = ChatMessageDTO.builder()
                    .sender(senderUsername)
                    .recipient(chatMessageDTO.getRecipient())
                    .content(savedMessage.getContent())
                    .timestamp(savedMessage.getTimestamp())
                    .isSystemMessage(false)
                    .build();

            // Send to the recipient
            messagingTemplate.convertAndSendToUser(
                chatMessageDTO.getRecipient(),
                "/queue/private",
                responseDTO
            );

            // Also send to the sender (for multi-device sync)
            messagingTemplate.convertAndSendToUser(
                senderUsername,
                "/queue/private",
                responseDTO
            );

        } catch (Exception e) {
            System.err.println("Error sending message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // REST API: get chat history
    @GetMapping("/api/chat/history/{username}")
    public ResponseEntity<List<ChatMessageDTO>> getChatHistory(
            @PathVariable String username,
            @RequestParam(defaultValue = "50") int limit,
            Authentication authentication) {

        String currentUsername = authentication.getName();

        List<ChatMessage> messages = chatMessageService.getChatHistory(currentUsername, username, limit);

        List<ChatMessageDTO> dtos = messages.stream()
                .map(msg -> ChatMessageDTO.builder()
                        .sender(msg.getSenderUsername())
                        .recipient(msg.getRecipientUsername())
                        .content(msg.getContent())
                        .timestamp(msg.getTimestamp())
                        .isSystemMessage(msg.getIsSystemMessage() != null ? msg.getIsSystemMessage() : false)
                        .build())
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    // REST API: mark messages as read
    @PostMapping("/api/chat/read/{username}")
    public ResponseEntity<Void> markAsRead(
            @PathVariable String username,
            Authentication authentication) {

        String currentUsername = authentication.getName();
        chatMessageService.markMessagesAsRead(username, currentUsername);

        return ResponseEntity.ok().build();
    }

    // REST API: get unread message count
    @GetMapping("/api/chat/unread-count")
    public ResponseEntity<Integer> getUnreadCount(Authentication authentication) {
        String currentUsername = authentication.getName();
        int count = chatMessageService.getUnreadCount(currentUsername);
        return ResponseEntity.ok(count);
    }

    // REST API: get conversation list
    @GetMapping("/api/chat/conversations")
    public ResponseEntity<List<ConversationDTO>> getConversations(Authentication authentication) {
        String currentUsername = authentication.getName();
        List<ConversationDTO> conversations = chatMessageService.getConversations(currentUsername);
        return ResponseEntity.ok(conversations);
    }

    // REST API: administrator sends a system message
    @PostMapping("/api/chat/admin/send-system-message")
    public ResponseEntity<?> sendSystemMessage(
            @RequestBody ChatMessageDTO chatMessageDTO,
            Authentication authentication) {
        try {
            System.out.println("[SYSTEM_MSG] Sending system message to: " + chatMessageDTO.getRecipient());
            String adminUsername = authentication.getName();

            // Check that the caller is an administrator
            boolean isAdmin = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

            if (!isAdmin) {
                System.err.println("[SYSTEM_MSG] Permission denied: " + adminUsername);
                return ResponseEntity.status(403).body("Only administrators can send system messages");
            }

            // Save the system message to the database
            ChatMessage savedMessage = chatMessageService.saveMessage(
                "System",  // Sender is shown as "System"
                chatMessageDTO.getRecipient(),
                chatMessageDTO.getContent(),
                true  // Mark as a system message
            );
            System.out.println("[SYSTEM_MSG] Message saved, ID: " + savedMessage.getId());

            // Build the response DTO
            ChatMessageDTO responseDTO = ChatMessageDTO.builder()
                    .sender("System")
                    .recipient(chatMessageDTO.getRecipient())
                    .content(savedMessage.getContent())
                    .timestamp(savedMessage.getTimestamp())
                    .isSystemMessage(true)
                    .build();

            // Send to the recipient
            System.out.println("[SYSTEM_MSG] Sending over WebSocket to user: " + chatMessageDTO.getRecipient());
            messagingTemplate.convertAndSendToUser(
                chatMessageDTO.getRecipient(),
                "/queue/private",
                responseDTO
            );
            System.out.println("[SYSTEM_MSG] WebSocket send complete");

            return ResponseEntity.ok().body("System message sent");
        } catch (Exception e) {
            System.err.println("[SYSTEM_MSG] Send failed: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body("Send failed: " + e.getMessage());
        }
    }

    // REST API: administrator broadcasts a system message to all users
    @PostMapping("/api/message/broadcast")
    public ResponseEntity<?> broadcastSystemMessage(
            @RequestBody java.util.Map<String, String> payload,
            Authentication authentication) {
        try {
            System.out.println("[BROADCAST] Broadcasting system message");

            // Check that the caller is an administrator
            boolean isAdmin = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

            if (!isAdmin) {
                System.err.println("[BROADCAST] Permission denied");
                return ResponseEntity.status(403).body("Only administrators can send system messages");
            }

            String message = payload.get("message");
            if (message == null || message.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Message content must not be empty");
            }

            // Load all users
            String adminUsername = authentication.getName();
            List<lut.cn.c2cplatform.entity.User> allUsers = chatMessageService.getAllUsers();
            System.out.println("[BROADCAST] Found " + allUsers.size() + " users");

            int successCount = 0;
            int failCount = 0;

            for (lut.cn.c2cplatform.entity.User user : allUsers) {
                // Skip the administrator themselves
                if (user.getUsername().equals(adminUsername)) {
                    System.out.println("[BROADCAST] Skipping administrator: " + user.getUsername());
                    continue;
                }

                try {
                    System.out.println("[BROADCAST] Sending to user: " + user.getUsername());

                    // Save the system message to the database
                    ChatMessage savedMessage = chatMessageService.saveMessage(
                        "System",
                        user.getUsername(),
                        message,
                        true
                    );

                    // Build the response DTO
                    ChatMessageDTO responseDTO = ChatMessageDTO.builder()
                            .sender("System")
                            .recipient(user.getUsername())
                            .content(savedMessage.getContent())
                            .timestamp(savedMessage.getTimestamp())
                            .isSystemMessage(true)
                            .build();

                    // Send to the user
                    messagingTemplate.convertAndSendToUser(
                        user.getUsername(),
                        "/queue/private",
                        responseDTO
                    );

                    successCount++;
                } catch (Exception e) {
                    System.err.println("[BROADCAST] Failed to send to " + user.getUsername() + ": " + e.getMessage());
                    e.printStackTrace();
                    failCount++;
                }
            }

            String result = String.format("Broadcast complete: %d sent, %d failed", successCount, failCount);
            System.out.println("[BROADCAST] " + result);
            return ResponseEntity.ok().body(result);
        } catch (Exception e) {
            System.err.println("[BROADCAST] Broadcast failed: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body("Broadcast failed: " + e.getMessage());
        }
    }
}
