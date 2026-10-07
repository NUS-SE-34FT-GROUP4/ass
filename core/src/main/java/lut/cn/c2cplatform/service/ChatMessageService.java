package lut.cn.c2cplatform.service;

import lut.cn.c2cplatform.dto.ConversationDTO;
import lut.cn.c2cplatform.entity.ChatMessage;
import lut.cn.c2cplatform.entity.User;
import lut.cn.c2cplatform.mapper.UserMapper;
import lut.cn.c2cplatform.repository.ChatMessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ChatMessageService {

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private UserMapper userMapper;

    public ChatMessage saveMessage(String senderUsername, String recipientUsername, String content, boolean isSystemMessage) {
        User recipient = userMapper.selectByUsername(recipientUsername);

        if (recipient == null) {
            throw new RuntimeException("Recipient not found");
        }

        Long senderId = null;
        if (!isSystemMessage) {
            User sender = userMapper.selectByUsername(senderUsername);
            if (sender == null) {
                throw new RuntimeException("Sender not found");
            }
            senderId = sender.getId();
        }

        ChatMessage message = ChatMessage.builder()
                .senderId(senderId)  // null for system messages
                .recipientId(recipient.getId())
                .content(content)
                .timestamp(new Date())
                .isRead(false)
                .senderUsername(senderUsername)
                .recipientUsername(recipientUsername)
                .isSystemMessage(isSystemMessage)
                .build();

        chatMessageRepository.save(message);
        return message;
    }

    public List<ChatMessage> getChatHistory(String username1, String username2, int limit) {
        // System messages are handled separately
        if ("System".equals(username2)) {
            User user1 = userMapper.selectByUsername(username1);
            if (user1 == null) {
                throw new RuntimeException("User not found");
            }
            return chatMessageRepository.findSystemMessages(user1.getId(), limit);
        }

        User user1 = userMapper.selectByUsername(username1);
        User user2 = userMapper.selectByUsername(username2);

        if (user1 == null || user2 == null) {
            throw new RuntimeException("User not found");
        }

        return chatMessageRepository.findHistoryBetweenUsers(user1.getId(), user2.getId(), limit);
    }

    public void markMessagesAsRead(String senderUsername, String recipientUsername) {
        // System messages are handled separately
        if ("System".equals(senderUsername)) {
            User recipient = userMapper.selectByUsername(recipientUsername);
            if (recipient == null) {
                throw new RuntimeException("Recipient not found");
            }
            chatMessageRepository.markSystemMessagesAsRead(recipient.getId());
            return;
        }

        User sender = userMapper.selectByUsername(senderUsername);
        User recipient = userMapper.selectByUsername(recipientUsername);

        if (sender == null || recipient == null) {
            throw new RuntimeException("User not found");
        }

        chatMessageRepository.markAsRead(sender.getId(), recipient.getId());
    }

    public int getUnreadCount(String username) {
        User user = userMapper.selectByUsername(username);
        if (user == null) {
            throw new RuntimeException("User not found");
        }
        return chatMessageRepository.countUnreadMessages(user.getId());
    }

    public List<ConversationDTO> getConversations(String username) {
        User currentUser = userMapper.selectByUsername(username);
        if (currentUser == null) {
            throw new RuntimeException("User not found");
        }

        // Usernames of everyone the user has chatted with
        List<String> partnerUsernames = chatMessageRepository.findConversationPartners(currentUser.getId());

        // Build the full conversation entry for each of them
        return partnerUsernames.stream()
            .map(partnerUsername -> {
                User partner = userMapper.selectByUsername(partnerUsername);
                if (partner == null) {
                    return null;
                }

                // Last message with this user
                ChatMessage lastMessage = chatMessageRepository.findLastMessageBetweenUsers(
                    currentUser.getId(), partner.getId()
                );

                // Unread message count
                int unreadCount = chatMessageRepository.countUnreadMessagesBetweenUsers(
                    currentUser.getId(), partner.getId()
                );

                return ConversationDTO.builder()
                    .userId(partner.getId())
                    .username(partner.getUsername())
                    .displayName(partner.getDisplayName() != null ? partner.getDisplayName() : partner.getUsername())
                    .avatar(partner.getAvatarUrl() != null ? partner.getAvatarUrl() :
                        "https://ui-avatars.com/api/?name=" + partner.getUsername() + "&background=007bff&color=fff&size=100")
                    .lastMessage(lastMessage != null ? lastMessage.getContent() : "")
                    .lastMessageTime(lastMessage != null && lastMessage.getTimestamp() != null ?
                        convertToLocalDateTime(lastMessage.getTimestamp()) : null)
                    .unreadCount(unreadCount)
                    .isOnline(false) // TODO: implement online status
                    .build();
            })
            .filter(conv -> conv != null)
            .collect(Collectors.toList());
    }

    // Time conversion helper
    private LocalDateTime convertToLocalDateTime(Date date) {
        if (date == null) {
            return null;
        }
        return date.toInstant()
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDateTime();
    }

    // All users (for broadcast messages)
    public List<User> getAllUsers() {
        return userMapper.selectAll();
    }
}
